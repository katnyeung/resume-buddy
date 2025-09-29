#!/usr/bin/env python3
"""
Docling Microservice for Resume Buddy
FastAPI service that provides document parsing via HTTP
"""

import os
os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import tempfile
import os
from pathlib import Path
import json
import requests
import logging
from typing import Dict, Any
import html

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

from docling.document_converter import DocumentConverter
from docling.datamodel.base_models import InputFormat

app = FastAPI(title="Docling Resume Parser", version="1.0.0")

# Initialize converter
converter = DocumentConverter()

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "docling_available": True,
        "message": "Docling Resume Parser Service"
    }

@app.post("/debug-url")
async def debug_url_request(request: dict):
    """Debug endpoint to test URL parsing"""
    logger.info(f"Debug endpoint received: {request}")
    return {"received": request, "type": type(request).__name__}

@app.post("/parse")
async def parse_document(file: UploadFile = File(...)):
    """Parse uploaded document and return structured content"""


    # Validate file type
    allowed_types = ["application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"]
    if file.content_type not in allowed_types:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type: {file.content_type}"
        )

    # Save uploaded file temporarily
    with tempfile.NamedTemporaryFile(delete=False, suffix=Path(file.filename).suffix) as temp_file:
        content = await file.read()
        temp_file.write(content)
        temp_file_path = temp_file.name

    try:
        # Parse with Docling
        result = converter.convert(temp_file_path)
        doc = result.document

        # Extract text and decode HTML entities
        raw_text = doc.export_to_text()
        raw_markdown = doc.export_to_markdown()

        # Decode HTML entities (&amp; -> &, &lt; -> <, etc.)
        decoded_text = html.unescape(raw_text) if raw_text else ""
        decoded_markdown = html.unescape(raw_markdown) if raw_markdown else ""

        # Extract structured content
        parsed_data = {
            "success": True,
            "filename": file.filename,
            "content_type": file.content_type,
            "text": decoded_text,
            "markdown": decoded_markdown,
            "metadata": {
                "title": getattr(doc, 'title', ''),
                "pages": len(doc.pages) if hasattr(doc, 'pages') else 1,
                "word_count": len(decoded_text.split()) if decoded_text else 0
            },
            "structure": []
        }

        # Extract structural elements
        if hasattr(doc, 'texts'):
            for element in doc.texts:
                element_text = element.text if hasattr(element, 'text') else str(element)
                parsed_data["structure"].append({
                    "type": element.label if hasattr(element, 'label') else 'text',
                    "text": html.unescape(element_text),
                    "confidence": getattr(element, 'confidence', 1.0)
                })

        # Extract tables if available
        if hasattr(doc, 'tables'):
            parsed_data["tables"] = []
            for table in doc.tables:
                parsed_data["tables"].append({
                    "rows": table.export_to_dataframe().to_dict('records') if hasattr(table, 'export_to_dataframe') else []
                })

        return JSONResponse(content=parsed_data)

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error parsing document: {str(e)}"
        )

    finally:
        # Clean up temporary file
        try:
            os.unlink(temp_file_path)
        except:
            pass

@app.post("/parse-text")
async def parse_text_only(file: UploadFile = File(...)):
    """Simple text extraction endpoint"""


    with tempfile.NamedTemporaryFile(delete=False, suffix=Path(file.filename).suffix) as temp_file:
        content = await file.read()
        temp_file.write(content)
        temp_file_path = temp_file.name

    try:
        result = converter.convert(temp_file_path)
        doc = result.document

        # Decode HTML entities
        raw_text = doc.export_to_text()
        decoded_text = html.unescape(raw_text) if raw_text else ""

        return {
            "success": True,
            "text": decoded_text,
            "word_count": len(decoded_text.split())
        }

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error extracting text: {str(e)}"
        )

    finally:
        os.unlink(temp_file_path)

class FileUrlRequest(BaseModel):
    file_url: str

@app.post("/parse-url")
async def parse_document_from_url(request: FileUrlRequest):
    """Parse document from URL and return structured content"""
    logger.info(f"Received parse-url request")
    logger.info(f"Request file_url: {request.file_url}")
    file_url = request.file_url

    try:
        # Fetch file from URL
        logger.info(f"Attempting to fetch file from URL: {file_url}")
        response = requests.get(file_url, timeout=30)
        logger.info(f"HTTP response status: {response.status_code}")
        logger.info(f"HTTP response headers: {dict(response.headers)}")
        response.raise_for_status()

        # Get content type from response headers
        content_type = response.headers.get('content-type', 'application/octet-stream')
        logger.info(f"Content type detected: {content_type}")
        logger.info(f"Response content length: {len(response.content)} bytes")

        # Validate file type
        allowed_types = ["application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"]
        if content_type not in allowed_types:
            logger.error(f"Unsupported file type: {content_type}. Allowed types: {allowed_types}")
            raise HTTPException(
                status_code=400,
                detail=f"Unsupported file type: {content_type}"
            )

        # Determine file extension from content type
        extension_map = {
            "application/pdf": ".pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document": ".docx",
            "text/plain": ".txt"
        }
        file_extension = extension_map.get(content_type, "")
        logger.info(f"Using file extension: {file_extension}")

        # Save content to temporary file
        with tempfile.NamedTemporaryFile(delete=False, suffix=file_extension) as temp_file:
            temp_file.write(response.content)
            temp_file_path = temp_file.name

        logger.info(f"Saved temporary file: {temp_file_path}")

        # Parse with Docling
        logger.info("Starting Docling conversion...")
        result = converter.convert(temp_file_path)
        doc = result.document
        logger.info("Docling conversion completed successfully")

        # Extract text and decode HTML entities
        raw_text = doc.export_to_text()
        raw_markdown = doc.export_to_markdown()

        # Decode HTML entities (&amp; -> &, &lt; -> <, etc.)
        decoded_text = html.unescape(raw_text) if raw_text else ""
        decoded_markdown = html.unescape(raw_markdown) if raw_markdown else ""

        # Extract structured content (same as original parse endpoint)
        parsed_data = {
            "success": True,
            "file_url": file_url,
            "content_type": content_type,
            "text": decoded_text,
            "markdown": decoded_markdown,
            "metadata": {
                "title": getattr(doc, 'title', ''),
                "pages": len(doc.pages) if hasattr(doc, 'pages') else 1,
                "word_count": len(decoded_text.split()) if decoded_text else 0
            },
            "structure": []
        }

        # Extract structural elements
        if hasattr(doc, 'texts'):
            for element in doc.texts:
                element_text = element.text if hasattr(element, 'text') else str(element)
                parsed_data["structure"].append({
                    "type": element.label if hasattr(element, 'label') else 'text',
                    "text": html.unescape(element_text),
                    "confidence": getattr(element, 'confidence', 1.0)
                })

        # Extract tables if available
        if hasattr(doc, 'tables'):
            parsed_data["tables"] = []
            for table in doc.tables:
                parsed_data["tables"].append({
                    "rows": table.export_to_dataframe().to_dict('records') if hasattr(table, 'export_to_dataframe') else []
                })

        logger.info("Successfully created parsed data response")
        return JSONResponse(content=parsed_data)

    except requests.RequestException as e:
        logger.error(f"Error fetching file from URL: {str(e)}")
        raise HTTPException(
            status_code=400,
            detail=f"Error fetching file from URL: {str(e)}"
        )
    except Exception as e:
        logger.error(f"Error parsing document: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Error parsing document: {str(e)}"
        )
    finally:
        # Clean up temporary file
        try:
            os.unlink(temp_file_path)
        except:
            pass

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)