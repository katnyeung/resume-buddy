#!/usr/bin/env python3
"""
RunPod Handler for Docling Document Parser
This is the entry point for RunPod serverless execution
"""

import os
os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"

import runpod
import tempfile
import base64
import requests
from pathlib import Path
import html
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

from docling.document_converter import DocumentConverter, PdfFormatOption, ImageFormatOption
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, EasyOcrOptions

# Initialize Docling converter (runs once on cold start)
logger.info("Initializing Docling converter...")

pdf_pipeline_options = PdfPipelineOptions()
pdf_pipeline_options.do_ocr = True
pdf_pipeline_options.do_table_structure = True
pdf_pipeline_options.table_structure_options.do_cell_matching = True
pdf_pipeline_options.images_scale = 2.0
pdf_pipeline_options.ocr_options = EasyOcrOptions(
    force_full_page_ocr=False,
    use_gpu=True,
    lang=["en", "fr", "de", "es"],
    confidence_threshold=0.3
)

image_pipeline_options = PdfPipelineOptions()
image_pipeline_options.do_ocr = True
image_pipeline_options.do_table_structure = True
image_pipeline_options.table_structure_options.do_cell_matching = True
image_pipeline_options.images_scale = 2.0
image_pipeline_options.ocr_options = EasyOcrOptions(
    force_full_page_ocr=True,
    use_gpu=True,
    lang=["en", "fr", "de", "es"],
    confidence_threshold=0.3
)

converter = DocumentConverter(
    format_options={
        InputFormat.PDF: PdfFormatOption(pipeline_options=pdf_pipeline_options),
        InputFormat.IMAGE: PdfFormatOption(pipeline_options=image_pipeline_options),
    }
)

logger.info("Docling converter initialized successfully")


def handler(job):
    """
    RunPod handler function

    Expected input format:
    {
        "input": {
            "file_base64": "base64-encoded-file",  # Option 1: Base64 file
            "file_url": "https://...",              # Option 2: URL to file
            "filename": "resume.pdf",
            "content_type": "application/pdf"
        }
    }

    Returns:
    {
        "text": "extracted text...",
        "markdown": "formatted markdown...",
        "content_type": "application/pdf",
        "filename": "resume.pdf",
        "metadata": {...}
    }
    """
    job_input = job["input"]

    logger.info(f"Processing job: {job.get('id', 'unknown')}")

    try:
        # Get file content
        if "file_url" in job_input and job_input["file_url"]:
            # Fetch from URL
            file_url = job_input["file_url"]
            logger.info(f"Fetching file from URL: {file_url}")

            response = requests.get(file_url, timeout=30)
            response.raise_for_status()

            content = response.content
            content_type = response.headers.get('content-type', 'application/pdf')
            filename = job_input.get("filename", "document.pdf")

        elif "file_base64" in job_input and job_input["file_base64"]:
            # Decode base64
            logger.info(f"Decoding base64 file: {job_input.get('filename', 'unknown')}")

            content = base64.b64decode(job_input["file_base64"])
            content_type = job_input.get("content_type", "application/pdf")
            filename = job_input.get("filename", "document.pdf")

        else:
            return {"error": "Either file_base64 or file_url must be provided"}

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

        # Save to temp file
        with tempfile.NamedTemporaryFile(delete=False, suffix=file_extension) as temp_file:
            temp_file.write(content)
            temp_file_path = temp_file.name

        logger.info(f"Saved temp file: {temp_file_path} ({len(content)} bytes)")

        try:
            # Parse with Docling
            logger.info("Starting Docling conversion...")
            result = converter.convert(temp_file_path)
            doc = result.document

            # Extract and clean text
            raw_text = doc.export_to_text()
            raw_markdown = doc.export_to_markdown()

            decoded_text = html.unescape(raw_text) if raw_text else ""
            decoded_markdown = html.unescape(raw_markdown) if raw_markdown else ""

            # Clean up artifacts
            decoded_text = decoded_text.replace('&nbsp;', ' ')
            decoded_markdown = decoded_markdown.replace('&nbsp;', ' ')

            logger.info(f"Extraction complete: {len(decoded_text)} characters, {len(doc.pages) if hasattr(doc, 'pages') else 1} pages")

            # Return result
            return {
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

        finally:
            # Clean up temp file
            try:
                os.unlink(temp_file_path)
            except:
                pass

    except Exception as e:
        logger.error(f"Error processing job: {str(e)}", exc_info=True)
        return {"error": str(e)}


# Start RunPod serverless handler
if __name__ == "__main__":
    logger.info("Starting RunPod handler...")
    runpod.serverless.start({"handler": handler})
