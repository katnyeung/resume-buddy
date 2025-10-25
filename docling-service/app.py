#!/usr/bin/env python3
"""
Docling Microservice for Resume Buddy
FastAPI service that provides document parsing via HTTP
"""

import os
os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"

from fastapi import FastAPI, File, UploadFile, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import tempfile
import os
from pathlib import Path
import json
import requests
import logging
from typing import Dict, Any, Optional
import html
import base64

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

from docling.document_converter import DocumentConverter, PdfFormatOption, ImageFormatOption
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, EasyOcrOptions

app = FastAPI(title="Docling Resume Parser", version="1.0.0")

# Configure PDF pipeline (optimized for digital PDFs with embedded text)
pdf_pipeline_options = PdfPipelineOptions()
pdf_pipeline_options.do_ocr = True  # OCR only if text extraction fails
pdf_pipeline_options.do_table_structure = True  # Detect resume tables/sections
pdf_pipeline_options.table_structure_options.do_cell_matching = True
pdf_pipeline_options.images_scale = 2.0  # Better quality for embedded images
pdf_pipeline_options.ocr_options = EasyOcrOptions(
    force_full_page_ocr=False,  # Fast text extraction first
    use_gpu=True,  # Auto-detect GPU, fallback to CPU
    lang=["en", "fr", "de", "es"],  # Multi-language resume support (EasyOCR codes)
    confidence_threshold=0.3  # Lower threshold to capture more text (default 0.5)
)

# Configure IMAGE pipeline (optimized for PNG/JPG screenshots)
image_pipeline_options = PdfPipelineOptions()
image_pipeline_options.do_ocr = True
image_pipeline_options.do_table_structure = True
image_pipeline_options.table_structure_options.do_cell_matching = True
image_pipeline_options.images_scale = 2.0  # 2x resolution for small text
image_pipeline_options.ocr_options = EasyOcrOptions(
    force_full_page_ocr=True,  # Comprehensive OCR for images
    use_gpu=True,
    lang=["en", "fr", "de", "es"],  # EasyOCR language codes
    confidence_threshold=0.3  # Lower threshold to capture more text (default 0.5)
)

# Initialize converter with format-specific optimizations
converter = DocumentConverter(
    format_options={
        InputFormat.PDF: PdfFormatOption(pipeline_options=pdf_pipeline_options),
        InputFormat.IMAGE: PdfFormatOption(pipeline_options=image_pipeline_options),
    }
)

logger.info("Docling converter initialized with optimized OCR settings:")
logger.info("  - PDF: Fast text extraction + OCR fallback")
logger.info("  - Images: Full-page OCR with 2x scaling")
logger.info("  - GPU: Auto-detect (fallback to CPU)")
logger.info("  - Languages: en, fr, de, es (EasyOCR)")
logger.info("  - Confidence threshold: 0.3 (lower = more text captured)")

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
    allowed_types = [
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "image/jpeg",
        "image/jpg",
        "image/png"
    ]
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

        # Debug: Log raw text length for troubleshooting
        logger.info(f"OCR extracted {len(raw_text)} characters from {file.filename}")

        # Decode HTML entities (&amp; -> &, &lt; -> <, etc.)
        # Apply multiple passes to handle nested/complex entities
        decoded_text = html.unescape(raw_text) if raw_text else ""
        decoded_markdown = html.unescape(raw_markdown) if raw_markdown else ""

        # Additional cleanup for common OCR artifacts
        decoded_text = decoded_text.replace('&nbsp;', ' ')  # Non-breaking spaces
        decoded_markdown = decoded_markdown.replace('&nbsp;', ' ')

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

        # Extract structural elements with better text handling
        if hasattr(doc, 'texts'):
            for element in doc.texts:
                element_text = element.text if hasattr(element, 'text') else str(element)
                # Clean up text artifacts
                cleaned_text = html.unescape(element_text)
                cleaned_text = cleaned_text.replace('&nbsp;', ' ')

                parsed_data["structure"].append({
                    "type": element.label if hasattr(element, 'label') else 'text',
                    "text": cleaned_text,
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
        allowed_types = [
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "image/jpeg",
            "image/jpg",
            "image/png"
        ]
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
            "text/plain": ".txt",
            "image/jpeg": ".jpg",
            "image/jpg": ".jpg",
            "image/png": ".png"
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

        # Extract structural elements with better text handling
        if hasattr(doc, 'texts'):
            for element in doc.texts:
                element_text = element.text if hasattr(element, 'text') else str(element)
                # Clean up text artifacts
                cleaned_text = html.unescape(element_text)
                cleaned_text = cleaned_text.replace('&nbsp;', ' ')

                parsed_data["structure"].append({
                    "type": element.label if hasattr(element, 'label') else 'text',
                    "text": cleaned_text,
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

class RunPodInput(BaseModel):
    """RunPod serverless input format"""
    file_base64: Optional[str] = None
    file_url: Optional[str] = None
    filename: Optional[str] = None
    content_type: Optional[str] = None

class RunPodRequest(BaseModel):
    """RunPod serverless request wrapper"""
    input: RunPodInput

@app.post("/runsync")
async def runpod_handler(request: RunPodRequest):
    """
    RunPod serverless handler endpoint
    Accepts input in RunPod format: {"input": {"file_base64": "...", "filename": "..."}}
    or {"input": {"file_url": "https://..."}}
    """
    logger.info("RunPod handler received request")

    try:
        input_data = request.input

        # Handle file URL input
        if input_data.file_url:
            logger.info(f"Processing file from URL: {input_data.file_url}")

            # Fetch file from URL
            response = requests.get(input_data.file_url, timeout=30)
            response.raise_for_status()

            content = response.content
            content_type = response.headers.get('content-type', 'application/pdf')
            filename = input_data.filename or "document.pdf"

        # Handle base64 input
        elif input_data.file_base64:
            logger.info(f"Processing base64 file: {input_data.filename}")

            # Decode base64
            content = base64.b64decode(input_data.file_base64)
            content_type = input_data.content_type or "application/pdf"
            filename = input_data.filename or "document.pdf"

        else:
            raise HTTPException(status_code=400, detail="Either file_base64 or file_url must be provided")

        # Validate file type
        allowed_types = [
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "image/jpeg",
            "image/jpg",
            "image/png"
        ]
        if content_type not in allowed_types:
            raise HTTPException(
                status_code=400,
                detail=f"Unsupported file type: {content_type}"
            )

        # Determine file extension
        extension_map = {
            "application/pdf": ".pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document": ".docx",
            "text/plain": ".txt",
            "image/jpeg": ".jpg",
            "image/jpg": ".jpg",
            "image/png": ".png"
        }
        file_extension = extension_map.get(content_type, Path(filename).suffix or ".pdf")

        # Save to temporary file
        with tempfile.NamedTemporaryFile(delete=False, suffix=file_extension) as temp_file:
            temp_file.write(content)
            temp_file_path = temp_file.name

        logger.info(f"Saved temporary file: {temp_file_path}")

        try:
            # Parse with Docling
            logger.info("Starting Docling conversion...")
            result = converter.convert(temp_file_path)
            doc = result.document
            logger.info("Docling conversion completed successfully")

            # Extract text and decode HTML entities
            raw_text = doc.export_to_text()
            raw_markdown = doc.export_to_markdown()

            decoded_text = html.unescape(raw_text) if raw_text else ""
            decoded_markdown = html.unescape(raw_markdown) if raw_markdown else ""

            # Build response
            output = {
                "text": decoded_text,
                "markdown": decoded_markdown,
                "content_type": content_type,
                "filename": filename,
                "metadata": {
                    "title": getattr(doc, 'title', ''),
                    "pages": len(doc.pages) if hasattr(doc, 'pages') else 1,
                    "word_count": len(decoded_text.split()) if decoded_text else 0
                }
            }

            logger.info(f"Successfully parsed document: {filename} ({len(decoded_text)} chars)")

            # Return in RunPod format
            return JSONResponse(content={"output": output})

        finally:
            # Clean up temporary file
            try:
                os.unlink(temp_file_path)
            except:
                pass

    except Exception as e:
        logger.error(f"Error in RunPod handler: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Error parsing document: {str(e)}"
        )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)