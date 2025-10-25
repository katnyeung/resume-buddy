# AI Analysis Prompt Templates

This directory contains configurable prompt templates for the AI-powered resume and job analysis features.

## Naming Convention

Prompts are numbered by **service** and **execution sequence**:

```
[sequence]-[service]-[purpose].txt
```

Example: `1-job-analysis-normalization-and-skills.txt`

## Active Prompts (Phase 11.6)

### Job Analysis Service (Resume API - Port 8080)

These prompts are called during job experience analysis in sequential order:

#### `1-job-analysis-normalization-and-skills.txt`
**Service**: `JobAnalysisService.analyzeJobComprehensive()`
**Purpose**: Normalizes job title and extracts skills in a single LLM call
**Variables**:
- `{jobTitle}`, `{company}`, `{startDate}`, `{endDate}`, `{description}`

**Returns**:
- Normalized job title, SOC codes, seniority level
- Extracted skills with categories and proficiency levels

---

#### `2-job-analysis-line-mapping-and-evaluation.txt`
**Service**: `JobAnalysisService.analyzeDescriptionAndEvaluate()` (**MERGED in Phase 11.6**)
**Purpose**: Maps resume lines to O*NET activities/tasks AND evaluates job quality
**Variables**:
- `{jobTitle}`, `{occupationTitle}`, `{company}`, `{seniorityLevel}`
- `{extractedSkills}`, `{descriptionLines}`
- `{onetActivities}`, `{onetTasks}`

**Returns**:
- Line-by-line O*NET mappings with confidence scores
- Recruiter insights (scan analysis, STARS suggestions, potential questions)
- Skill-to-task mappings
- Overall job evaluation (impact/technical/leadership scores)

**Key Features**:
- **Quantity Requirements**: 3-5 potential questions, 2-3 STARS suggestions per line
- Combines what used to be 2 separate calls (saves ~5-10 seconds)

---

#### `3-job-analysis-task-coverage-insights.txt`
**Service**: `JobAnalysisService.generateTaskCoverageInsights()`
**Purpose**: Generates career insights from O*NET task coverage
**Variables**:
- `{jobTitle}`, `{extractedSkills}`
- `{strongTasks}`, `{moderateTasks}`, `{weakTasks}`, `{noneTasks}`

**Returns**:
- Skill themes (5-6 categories with 0-10 scores for radar chart)
- Career summary, key strengths (O*NET task patterns)
- Growth opportunities, task usage guide

**Note**: Generated once during analysis, cached in database

---

### Resume Analysis Service (Resume API - Port 8080)

#### `4-resume-analysis-line-by-line.txt`
**Service**: `ResumeAnalysisService.analyzeResume()`
**Purpose**: Analyzes raw resume text line-by-line
**Variables**:
- `{resumeLines}`, `{lineCount}`

**Returns**:
- Line-by-line section classification
- Content grouping (jobs, projects, education)
- Structural analysis

---

## Removed Prompts (Phase 11.6 Cleanup)

These prompts were deleted as part of the LLM call optimization:

- ❌ `description-activity-mapping-prompt.txt` - Merged into #2
- ❌ `recruiter-evaluation-prompt.txt` - Merged into #2
- ❌ `job-normalization-prompt.txt` - Replaced by #1
- ❌ `system-prompt.txt` - No longer used

---

## Customization

### How to Edit Prompts

1. **Edit the template files** directly in this directory
2. **Use template variables** in the format `{variableName}`
3. **Restart the backend** to reload the templates

### Example Customization

To modify STARS suggestions requirements in `2-job-analysis-line-mapping-and-evaluation.txt`:

```
**STARS Analysis** - How to improve this line using STARS framework (2-3 actionable suggestions):
- Focus on what's MISSING or unclear in this specific line
- Each suggestion should address a specific STARS gap
- Examples:
  - "Add Situation: Explain the business problem (e.g., '...')"
  - "Enhance Result: Link to business outcomes (e.g., '...')"
```

### Quantity Requirements

Some prompts enforce minimum quantities:

- **Prompt #2**: 3-5 potential questions, 2-3 STARS suggestions per line
- **Prompt #3**: 5-6 skill themes

Adjust these in the `## CRITICAL OUTPUT REQUIREMENTS` section.

---

## Performance Notes

**Phase 11.6 Optimization**:
- Reduced job analysis from **4 LLM calls → 3 LLM calls**
- Saved **5-10 seconds** per analysis
- Cost reduction: **~$0.003** per job (~25% savings)

**Current Call Count**:
1. Normalization + skills (~2500 tokens)
2. Line mapping + evaluation (~3000 tokens) ← MERGED
3. Task coverage insights (~1500 tokens)

**Total**: ~7000 tokens (~$0.014 per analysis with Grok)

---

## Troubleshooting

### Templates Not Loading

Check logs for:
```
INFO: Loaded analysis prompt template
```

If missing, verify file names match the code references.

### Invalid JSON Response

If the AI returns invalid JSON:
1. Check the prompt's JSON format examples
2. Verify `## CRITICAL OUTPUT REQUIREMENTS` section is clear
3. Lower temperature for more consistent responses

### Missing STARS Suggestions

If getting fewer than expected suggestions:
1. Check `**QUANTITY REQUIREMENTS**` section in prompt #2
2. Verify examples show 2-3 suggestions
3. Ensure prompt includes "MUST provide 2-3 actionable suggestions"

---

## Advanced Configuration

To modify AI behavior beyond prompts:

1. **Model**: Change in `application.yml` → `app.openai.model: grok-4-fast-reasoning`
2. **Temperature**: Edit `AIAnalysisService.java` → `requestBody.put("temperature", 0.3)`
3. **Max Tokens**: Edit `AIAnalysisService.java` → `requestBody.put("max_tokens", 4000)`
