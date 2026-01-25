package com.resumebuddy.service;

import com.resumebuddy.model.dto.TailoredResumeResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

@Service
@Slf4j
public class DocxGeneratorService {

    // Margin in twips (1/20 of a point, 1440 twips = 1 inch)
    private static final BigInteger MARGIN_NORMAL = BigInteger.valueOf(1440); // 1 inch
    private static final BigInteger MARGIN_NARROW = BigInteger.valueOf(720);  // 0.5 inch

    /**
     * Generate a .docx file from the tailored resume response
     */
    public byte[] generateDocx(TailoredResumeResponseDto response) throws IOException {
        log.info("Generating DOCX for resume {}", response.getResumeId());

        try (XWPFDocument document = new XWPFDocument()) {
            // Set narrow margins for more content space
            setMargins(document);

            // Add contact info header
            addContactSection(document, response.getContactInfo());

            // Add horizontal line
            addHorizontalLine(document);

            // Add summary
            if (response.getSummary() != null && !response.getSummary().isEmpty()) {
                addSectionHeader(document, "PROFESSIONAL SUMMARY");
                addParagraph(document, response.getSummary());
                addEmptyLine(document);
            }

            // Add skills
            if (response.getSkills() != null && !response.getSkills().isEmpty()) {
                addSectionHeader(document, "SKILLS");
                addSkillsSection(document, response.getSkills());
                addEmptyLine(document);
            }

            // Add experience
            if (response.getExperiences() != null && !response.getExperiences().isEmpty()) {
                addSectionHeader(document, "PROFESSIONAL EXPERIENCE");
                for (TailoredResumeResponseDto.ExperienceSection exp : response.getExperiences()) {
                    addExperienceEntry(document, exp);
                }
            }

            // Add projects
            if (response.getProjects() != null && !response.getProjects().isEmpty()) {
                addSectionHeader(document, "PROJECTS");
                for (TailoredResumeResponseDto.ProjectSection proj : response.getProjects()) {
                    addProjectEntry(document, proj);
                }
            }

            // Add education
            if (response.getEducations() != null && !response.getEducations().isEmpty()) {
                addSectionHeader(document, "EDUCATION");
                for (TailoredResumeResponseDto.EducationSection edu : response.getEducations()) {
                    addEducationEntry(document, edu);
                }
            }

            // Add certifications
            if (response.getCertifications() != null && !response.getCertifications().isEmpty()) {
                addSectionHeader(document, "CERTIFICATIONS");
                for (String cert : response.getCertifications()) {
                    addBulletPoint(document, cert);
                }
            }

            // Write to byte array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            log.info("DOCX generation completed for resume {}", response.getResumeId());
            return out.toByteArray();
        }
    }

    /**
     * Set document margins
     */
    private void setMargins(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.addNewPgMar();
        pageMar.setLeft(MARGIN_NARROW);
        pageMar.setRight(MARGIN_NARROW);
        pageMar.setTop(MARGIN_NARROW);
        pageMar.setBottom(MARGIN_NARROW);
    }

    /**
     * Add contact information at the top with professional styling
     */
    private void addContactSection(XWPFDocument document, String contactInfo) {
        if (contactInfo == null || contactInfo.isEmpty()) return;

        String[] lines = contactInfo.split("\n");
        for (int i = 0; i < lines.length; i++) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            paragraph.setSpacingAfter(0);

            if (i == 0) {
                // Name - larger, bold, with color
                XWPFRun run = paragraph.createRun();
                run.setText(lines[i].trim().toUpperCase());
                run.setBold(true);
                run.setFontSize(18);
                run.setFontFamily("Calibri");
                run.setColor("1F4E79"); // Professional dark blue
                paragraph.setSpacingAfter(80);
            } else {
                // Contact details - compact with visual separators
                String line = lines[i].trim();
                String[] parts = line.split("\\|");

                for (int j = 0; j < parts.length; j++) {
                    XWPFRun run = paragraph.createRun();
                    run.setText(parts[j].trim());
                    run.setFontSize(10);
                    run.setFontFamily("Calibri");

                    if (j < parts.length - 1) {
                        XWPFRun sepRun = paragraph.createRun();
                        sepRun.setText("  |  ");
                        sepRun.setFontSize(10);
                        sepRun.setFontFamily("Calibri");
                        sepRun.setColor("999999");
                    }
                }
            }
        }
    }

    /**
     * Add a section header with professional styling
     */
    private void addSectionHeader(XWPFDocument document, String header) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(300); // More space before header
        paragraph.setSpacingAfter(80);

        XWPFRun run = paragraph.createRun();
        run.setText(header);
        run.setBold(true);
        run.setFontSize(11);
        run.setFontFamily("Calibri");
        run.setColor("1F4E79"); // Matching header color

        // Add thick bottom border for visual separation
        paragraph.setBorderBottom(Borders.THICK);
    }

    /**
     * Add a regular paragraph
     */
    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(100);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(11);
        run.setFontFamily("Calibri");
    }

    /**
     * Add skills in a wrapped format with separators
     */
    private void addSkillsSection(XWPFDocument document, List<String> skills) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);

        // Add skills with bullet separators for better readability
        for (int i = 0; i < skills.size(); i++) {
            XWPFRun run = paragraph.createRun();
            run.setText(skills.get(i));
            run.setFontSize(10);
            run.setFontFamily("Calibri");

            // Add separator except for last item
            if (i < skills.size() - 1) {
                XWPFRun sepRun = paragraph.createRun();
                sepRun.setText("  •  ");
                sepRun.setFontSize(10);
                sepRun.setFontFamily("Calibri");
                sepRun.setColor("999999");
            }
        }
    }

    /**
     * Add an experience entry with tab stop for date alignment
     */
    private void addExperienceEntry(XWPFDocument document, TailoredResumeResponseDto.ExperienceSection exp) {
        // Create paragraph with right-aligned tab stop for dates
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(150);

        // Add right-aligned tab stop at page width (10800 twips = ~7.5 inches for A4 with 0.5" margins)
        CTTabStop tabStop = paragraph.getCTP().addNewPPr().addNewTabs().addNewTab();
        tabStop.setVal(STTabJc.RIGHT);
        tabStop.setPos(BigInteger.valueOf(10080)); // Right margin position

        // Job title (bold)
        XWPFRun titleRun = paragraph.createRun();
        titleRun.setText(exp.getJobTitle());
        titleRun.setBold(true);
        titleRun.setFontSize(11);
        titleRun.setFontFamily("Calibri");

        // Company (italic)
        XWPFRun companyRun = paragraph.createRun();
        companyRun.setText(" at " + exp.getCompany());
        companyRun.setFontSize(11);
        companyRun.setFontFamily("Calibri");
        companyRun.setItalic(true);

        // Tab to push dates to the right
        XWPFRun tabRun = paragraph.createRun();
        tabRun.addTab();

        // Dates (right-aligned via tab stop)
        XWPFRun dateRun = paragraph.createRun();
        dateRun.setText(exp.getDates());
        dateRun.setFontSize(10);
        dateRun.setFontFamily("Calibri");
        dateRun.setColor("666666");

        // Bullet points
        if (exp.getBulletPoints() != null) {
            for (String bullet : exp.getBulletPoints()) {
                addBulletPoint(document, bullet);
            }
        }
    }

    /**
     * Add an education entry with tab stop for date alignment
     */
    private void addEducationEntry(XWPFDocument document, TailoredResumeResponseDto.EducationSection edu) {
        // Create paragraph with right-aligned tab stop
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(100);

        // Add right-aligned tab stop
        CTTabStop tabStop = paragraph.getCTP().addNewPPr().addNewTabs().addNewTab();
        tabStop.setVal(STTabJc.RIGHT);
        tabStop.setPos(BigInteger.valueOf(10080));

        // Degree (bold)
        XWPFRun degreeRun = paragraph.createRun();
        degreeRun.setText(edu.getDegree());
        degreeRun.setBold(true);
        degreeRun.setFontSize(11);
        degreeRun.setFontFamily("Calibri");

        // Institution
        XWPFRun instRun = paragraph.createRun();
        instRun.setText(" — " + edu.getInstitution());
        instRun.setFontSize(11);
        instRun.setFontFamily("Calibri");

        // GPA if present
        if (edu.getGpa() != null && !edu.getGpa().isEmpty()) {
            XWPFRun gpaRun = paragraph.createRun();
            gpaRun.setText(" (GPA: " + edu.getGpa() + ")");
            gpaRun.setFontSize(10);
            gpaRun.setFontFamily("Calibri");
            gpaRun.setColor("666666");
        }

        // Tab to push date to the right
        if (edu.getGraduationDate() != null && !edu.getGraduationDate().isEmpty()) {
            XWPFRun tabRun = paragraph.createRun();
            tabRun.addTab();

            XWPFRun dateRun = paragraph.createRun();
            dateRun.setText(edu.getGraduationDate());
            dateRun.setFontSize(10);
            dateRun.setFontFamily("Calibri");
            dateRun.setColor("666666");
        }
    }

    /**
     * Add a project entry
     */
    private void addProjectEntry(XWPFDocument document, TailoredResumeResponseDto.ProjectSection proj) {
        // Project name
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setSpacingBefore(150);

        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setText(proj.getProjectName());
        titleRun.setBold(true);
        titleRun.setFontSize(11);
        titleRun.setFontFamily("Calibri");

        // URL if present
        if (proj.getUrl() != null && !proj.getUrl().isEmpty()) {
            XWPFRun urlRun = titleParagraph.createRun();
            urlRun.setText(" | " + proj.getUrl());
            urlRun.setFontSize(10);
            urlRun.setItalic(true);
            urlRun.setFontFamily("Calibri");
            urlRun.setColor("0563C1"); // Link blue color
        }

        // Description
        if (proj.getDescription() != null && !proj.getDescription().isEmpty()) {
            addBulletPoint(document, proj.getDescription());
        }

        // Technologies
        if (proj.getTechnologies() != null && !proj.getTechnologies().isEmpty()) {
            XWPFParagraph techParagraph = document.createParagraph();
            techParagraph.setIndentationLeft(360);
            techParagraph.setSpacingAfter(50);

            XWPFRun techLabelRun = techParagraph.createRun();
            techLabelRun.setText("Technologies: ");
            techLabelRun.setBold(true);
            techLabelRun.setFontSize(10);
            techLabelRun.setFontFamily("Calibri");

            XWPFRun techRun = techParagraph.createRun();
            techRun.setText(String.join(", ", proj.getTechnologies()));
            techRun.setFontSize(10);
            techRun.setFontFamily("Calibri");
        }
    }

    /**
     * Add a bullet point
     */
    private void addBulletPoint(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(360); // Indent for bullet
        paragraph.setSpacingAfter(50);

        XWPFRun bulletRun = paragraph.createRun();
        bulletRun.setText("• ");
        bulletRun.setFontSize(11);
        bulletRun.setFontFamily("Calibri");

        XWPFRun textRun = paragraph.createRun();
        textRun.setText(text);
        textRun.setFontSize(11);
        textRun.setFontFamily("Calibri");
    }

    /**
     * Add a horizontal line
     */
    private void addHorizontalLine(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setBorderBottom(Borders.SINGLE);
        paragraph.setSpacingAfter(100);
    }

    /**
     * Add an empty line for spacing
     */
    private void addEmptyLine(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(50);
    }
}
