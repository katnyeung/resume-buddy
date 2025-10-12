package com.resumebuddy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock O*NET Service for development
 * Returns static O*NET data for common SOC codes
 * Replace this with real ONetIntegrationService when API access is granted
 */
@Service
@Slf4j
public class MockONetService {

    private static final Map<String, String> MOCK_ONET_DATA = new HashMap<>();

    static {
        // Software Developer (15-1252.00)
        MOCK_ONET_DATA.put("15-1252.00", """
            {
              "code": "15-1252.00",
              "title": "Software Developers",
              "description": "Research, design, and develop computer and network software or specialized utility programs. Analyze user needs and develop software solutions, applying principles and techniques of computer science, engineering, and mathematical analysis. Update software or enhance existing software capabilities. May work with computer hardware engineers to integrate hardware and software systems, and develop specifications and performance requirements. May maintain databases within an application area, working individually or coordinating database development as part of a team.",
              "tasks": [
                "Analyze information to determine, recommend, and plan installation of a new system or modification of an existing system",
                "Analyze user needs and software requirements to determine feasibility of design within time and cost constraints",
                "Confer with data processing or project managers to obtain information on limitations or capabilities for data processing projects",
                "Consult with customers or other departments on project status, proposals, or technical issues, such as software system design or maintenance",
                "Coordinate installation of software system",
                "Design and develop software systems, using scientific analysis and mathematical models to predict and measure outcomes and design consequences",
                "Develop or direct software system testing or validation procedures",
                "Direct software programming and development of documentation",
                "Monitor functioning of equipment to ensure system operates in conformance with specifications",
                "Store, retrieve, and manipulate data for analysis of system capabilities and requirements"
              ],
              "detailed_work_activities": [
                {"name": "Analyze data to identify or resolve operational problems", "importance": 85},
                {"name": "Apply mathematical principles or statistical approaches to solve problems", "importance": 82},
                {"name": "Collaborate with others to determine design specifications", "importance": 88},
                {"name": "Design software applications", "importance": 95},
                {"name": "Develop computer or online applications", "importance": 93},
                {"name": "Develop models of information or communications systems", "importance": 78},
                {"name": "Document design or development procedures", "importance": 75},
                {"name": "Install computer software", "importance": 68},
                {"name": "Modify software programs to improve performance", "importance": 87},
                {"name": "Test software performance", "importance": 90}
              ],
              "skills": [
                {"name": "Programming", "level": 85},
                {"name": "Critical Thinking", "level": 80},
                {"name": "Complex Problem Solving", "level": 82},
                {"name": "Systems Analysis", "level": 78},
                {"name": "Systems Evaluation", "level": 75},
                {"name": "Reading Comprehension", "level": 70},
                {"name": "Active Learning", "level": 72},
                {"name": "Mathematics", "level": 68},
                {"name": "Technology Design", "level": 85},
                {"name": "Time Management", "level": 65}
              ],
              "knowledge": [
                {"name": "Computers and Electronics", "level": 90},
                {"name": "Engineering and Technology", "level": 75},
                {"name": "Mathematics", "level": 70},
                {"name": "Design", "level": 72},
                {"name": "English Language", "level": 65},
                {"name": "Customer and Personal Service", "level": 55}
              ],
              "technology_skills": [
                "Java", "Python", "JavaScript", "C++", "SQL",
                "Git", "Docker", "Kubernetes", "AWS", "React",
                "Node.js", "MongoDB", "Redis", "Jenkins", "Agile"
              ]
            }
            """);

        // Computer Programmers (15-1251.00)
        MOCK_ONET_DATA.put("15-1251.00", """
            {
              "code": "15-1251.00",
              "title": "Computer Programmers",
              "description": "Create, modify, and test the code and scripts that allow computer applications to run. Work from specifications drawn up by software and web developers or other individuals. May develop and write computer programs to store, locate, and retrieve specific documents, data, and information.",
              "tasks": [
                "Correct errors by making appropriate changes and rechecking the program to ensure that the desired results are produced",
                "Conduct trial runs of programs and software applications to be sure they will produce the desired information",
                "Write, update, and maintain computer programs or software packages",
                "Write, analyze, review, and rewrite programs",
                "Perform or direct revision, repair, or expansion of existing programs",
                "Consult with managerial, engineering, and technical personnel to clarify program intent",
                "Compile and write documentation of program development",
                "Prepare detailed workflow charts and diagrams",
                "Perform systems analysis and programming tasks to maintain and control the use of computer systems software"
              ],
              "detailed_work_activities": [
                {"name": "Write computer programming code", "importance": 95},
                {"name": "Update computer database information", "importance": 78},
                {"name": "Analyze data to identify or resolve operational problems", "importance": 80},
                {"name": "Coordinate software or hardware installation", "importance": 65},
                {"name": "Develop computer or online applications", "importance": 88},
                {"name": "Document technical design details", "importance": 75},
                {"name": "Modify software programs to improve performance", "importance": 90},
                {"name": "Test software performance", "importance": 92},
                {"name": "Collaborate with others to resolve information technology issues", "importance": 72}
              ],
              "skills": [
                {"name": "Programming", "level": 90},
                {"name": "Reading Comprehension", "level": 75},
                {"name": "Critical Thinking", "level": 78},
                {"name": "Active Learning", "level": 70},
                {"name": "Complex Problem Solving", "level": 80},
                {"name": "Mathematics", "level": 65},
                {"name": "Writing", "level": 68}
              ],
              "knowledge": [
                {"name": "Computers and Electronics", "level": 88},
                {"name": "Engineering and Technology", "level": 70},
                {"name": "Mathematics", "level": 65},
                {"name": "English Language", "level": 60},
                {"name": "Design", "level": 58}
              ],
              "technology_skills": [
                "Java", "C++", "Python", "JavaScript", "SQL",
                "HTML", "CSS", "XML", "JSON", "REST APIs",
                "Git", "Linux", "Windows", "Debugging Tools"
              ]
            }
            """);

        // Web Developers (15-1254.00)
        MOCK_ONET_DATA.put("15-1254.00", """
            {
              "code": "15-1254.00",
              "title": "Web Developers",
              "description": "Develop and implement websites, web applications, application databases, and interactive web interfaces. Evaluate code to ensure that it is properly structured, meets industry standards, and is compatible with browsers and devices. Optimize website performance, scalability, and server-side code and processes. May develop website infrastructure and integrate websites with other computer applications.",
              "tasks": [
                "Design, build, or maintain web sites",
                "Write supporting code for Web applications or Web sites",
                "Design and implement user-driven templates, databases, and interfaces",
                "Develop databases that support Web applications and Web sites",
                "Perform Web site tests according to planned schedules",
                "Perform or direct Web site updates",
                "Identify problems uncovered by testing or customer feedback",
                "Evaluate code to ensure that it is valid, is properly structured, meets industry standards",
                "Maintain understanding of current Web technologies and programming practices"
              ],
              "detailed_work_activities": [
                {"name": "Write computer programming code", "importance": 92},
                {"name": "Create electronic data backup to prevent loss of information", "importance": 70},
                {"name": "Develop computer or online applications", "importance": 90},
                {"name": "Develop models of information or communications systems", "importance": 75},
                {"name": "Document technical design details", "importance": 78},
                {"name": "Test software performance", "importance": 88},
                {"name": "Update website content", "importance": 85},
                {"name": "Collaborate with others to determine design specifications", "importance": 80}
              ],
              "skills": [
                {"name": "Programming", "level": 88},
                {"name": "Critical Thinking", "level": 75},
                {"name": "Active Learning", "level": 72},
                {"name": "Reading Comprehension", "level": 70},
                {"name": "Complex Problem Solving", "level": 78}
              ],
              "knowledge": [
                {"name": "Computers and Electronics", "level": 85},
                {"name": "Engineering and Technology", "level": 68},
                {"name": "Design", "level": 75},
                {"name": "Communications and Media", "level": 65},
                {"name": "English Language", "level": 62}
              ],
              "technology_skills": [
                "JavaScript", "HTML", "CSS", "React", "Vue.js",
                "Node.js", "Express", "MongoDB", "PostgreSQL",
                "REST APIs", "GraphQL", "Git", "Webpack", "NPM"
              ]
            }
            """);

        // Data Scientists (15-2051.00)
        MOCK_ONET_DATA.put("15-2051.00", """
            {
              "code": "15-2051.00",
              "title": "Data Scientists",
              "description": "Develop and implement a set of techniques or analytics applications to transform raw data into meaningful information using data-oriented programming languages and visualization software. Apply data mining, data modeling, natural language processing, and machine learning to extract and analyze information from large structured and unstructured datasets. Visualize, interpret, and report data findings. May create dynamic data reports.",
              "tasks": [
                "Analyze large amounts of information to discover trends and patterns",
                "Apply feature selection algorithms to models",
                "Apply sampling techniques to determine groups to be surveyed",
                "Clean and manipulate raw data using statistical software",
                "Deliver oral or written presentations of analysis findings to management",
                "Design and implement databases to store and structure data",
                "Develop and implement algorithms or statistical models",
                "Identify business problems or management objectives that can be addressed through data analysis"
              ],
              "detailed_work_activities": [
                {"name": "Analyze data to identify trends or relationships", "importance": 95},
                {"name": "Apply mathematical principles or statistical approaches", "importance": 92},
                {"name": "Design computer modeling or simulation programs", "importance": 85},
                {"name": "Develop scientific or mathematical models", "importance": 88},
                {"name": "Prepare data for analysis", "importance": 90},
                {"name": "Present research results to others", "importance": 78},
                {"name": "Update knowledge about emerging technologies", "importance": 82}
              ],
              "skills": [
                {"name": "Programming", "level": 85},
                {"name": "Mathematics", "level": 88},
                {"name": "Critical Thinking", "level": 90},
                {"name": "Complex Problem Solving", "level": 87},
                {"name": "Systems Analysis", "level": 82}
              ],
              "knowledge": [
                {"name": "Mathematics", "level": 88},
                {"name": "Computers and Electronics", "level": 85},
                {"name": "Engineering and Technology", "level": 75}
              ],
              "technology_skills": [
                "Python", "R", "SQL", "TensorFlow", "PyTorch",
                "Pandas", "NumPy", "Scikit-learn", "Jupyter",
                "Tableau", "Power BI", "Spark", "Hadoop"
              ]
            }
            """);

        // Information Security Analysts (15-1212.00)
        MOCK_ONET_DATA.put("15-1212.00", """
            {
              "code": "15-1212.00",
              "title": "Information Security Analysts",
              "description": "Plan, implement, upgrade, or monitor security measures for the protection of computer networks and information. Assess system vulnerabilities for security risks and propose and implement risk mitigation strategies. May ensure appropriate security controls are in place that will safeguard digital files and vital electronic infrastructure. May respond to computer security breaches and viruses.",
              "tasks": [
                "Develop plans to safeguard computer files against accidental or unauthorized modification",
                "Encrypt data transmissions and erect firewalls to conceal confidential information",
                "Monitor current reports of computer viruses to determine when to update virus protection systems",
                "Perform risk assessments and execute tests of data processing system",
                "Modify computer security files to incorporate new software",
                "Coordinate implementation of computer system plan with establishment personnel",
                "Train users and promote security awareness to ensure system security"
              ],
              "detailed_work_activities": [
                {"name": "Analyze security of systems, network, or data", "importance": 95},
                {"name": "Develop organizational policies or programs", "importance": 82},
                {"name": "Implement security measures for computer or information systems", "importance": 93},
                {"name": "Maintain computer equipment or software", "importance": 78},
                {"name": "Monitor the security of digital information", "importance": 90},
                {"name": "Provide technical support for software maintenance", "importance": 75},
                {"name": "Train others in computer interface or software use", "importance": 70}
              ],
              "skills": [
                {"name": "Critical Thinking", "level": 85},
                {"name": "Complex Problem Solving", "level": 82},
                {"name": "Systems Analysis", "level": 88},
                {"name": "Systems Evaluation", "level": 85},
                {"name": "Programming", "level": 75}
              ],
              "knowledge": [
                {"name": "Computers and Electronics", "level": 90},
                {"name": "Telecommunications", "level": 78},
                {"name": "Engineering and Technology", "level": 72}
              ],
              "technology_skills": [
                "Network Security", "Penetration Testing", "SIEM",
                "Firewall Management", "IDS/IPS", "Cryptography",
                "Python", "PowerShell", "Wireshark", "Metasploit"
              ]
            }
            """);
    }

    /**
     * Get mock O*NET data for a given SOC code
     * Falls back to Software Developer data if code not found
     */
    public String getMockONetData(String socCode) {
        log.info("Fetching mock O*NET data for SOC code: {}", socCode);

        String data = MOCK_ONET_DATA.get(socCode);

        if (data == null) {
            log.warn("No mock data found for SOC code: {}, using Software Developer as fallback", socCode);
            data = MOCK_ONET_DATA.get("15-1252.00");
        }

        return data;
    }

    /**
     * Check if mock data exists for a SOC code
     */
    public boolean hasMockData(String socCode) {
        return MOCK_ONET_DATA.containsKey(socCode);
    }

    /**
     * Get all available mock SOC codes
     */
    public String[] getAvailableSocCodes() {
        return MOCK_ONET_DATA.keySet().toArray(new String[0]);
    }
}
