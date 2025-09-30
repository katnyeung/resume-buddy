# AI Analysis Prompt Templates

This directory contains configurable prompt templates for the AI-powered resume analysis feature.

## Files

### `system-prompt.txt`
The system-level prompt that defines the AI's role and behavior. This sets the context for how the AI should respond.

**Default**: "You are a resume analysis expert..."

### `resume-analysis-prompt.txt`
The main analysis prompt that instructs the AI on what to analyze and how to structure the response.

**Available Variables**:
- `{resumeLines}` - Replaced with the actual resume lines to analyze
- `{lineCount}` - Replaced with the total number of lines (available but not used by default)

## Customization

### How to Edit Prompts

1. **Edit the template files** directly in this directory
2. **Use template variables** in the format `{variableName}`
3. **Restart the backend** to reload the templates

### Example Customization

If you want to add a new variable:

**In `resume-analysis-prompt.txt`**:
```
Analyze {lineCount} lines from the following resume:
{resumeLines}
```

The `{resumeLines}` and `{lineCount}` variables will be automatically replaced with actual values.

### Available Variables

Currently supported template variables:
- `{resumeLines}` - The formatted list of resume lines
- `{lineCount}` - Total number of lines in the resume

### Section Types

The analysis recognizes these section types:
- `CONTACT` - Contact information (name, email, phone, etc.)
- `SUMMARY` - Professional summary or objective
- `EXPERIENCE` - Work experience entries
- `EDUCATION` - Education history
- `SKILLS` - Technical and soft skills
- `CERTIFICATIONS` - Professional certifications
- `PROJECTS` - Personal or professional projects
- `OTHER` - Any other content

### Group Types

Lines can be grouped with these types:
- `JOB` - All lines describing a single job/position
- `PROJECT` - All lines describing a single project
- `EDUCATION_ITEM` - All lines describing a degree/course
- `SKILL_CATEGORY` - Related skills grouped together
- `null` - Line not part of any group

## Troubleshooting

### Templates Not Loading

If templates fail to load, the service will fall back to hardcoded defaults. Check the logs for:
```
INFO: Loaded system prompt template
INFO: Loaded analysis prompt template
```

### Invalid JSON Response

If the AI returns invalid JSON:
1. Check the prompt is clear about JSON format requirements
2. Ensure the example format is correct
3. Consider adjusting the `temperature` parameter in `AIAnalysisService.java` (lower = more consistent)

## Advanced Configuration

To modify the AI behavior beyond prompts:

1. **Model**: Change in `.env` → `OPENAI_MODEL=grok-beta`
2. **Temperature**: Edit `AIAnalysisService.java` → `requestBody.put("temperature", 0.3)`
3. **Max Tokens**: Edit `AIAnalysisService.java` → `requestBody.put("max_tokens", 4000)`