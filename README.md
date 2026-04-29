# dms-backend

Spring Boot backend for Universal-DMS.

Build and run using Maven so the whole module is compiled together. Do not try to compile or run a single .java file (e.g., AuthService.java) directly — that will cause errors like "package com.dms.dao does not exist" because the compiler can’t see the rest of the project.

Quick start:
- Prerequisite: Java 21 and Maven installed (or use the provided Maven Wrapper mvnw/mvnw.cmd).
- From this folder (dms-backend), run:
  - Windows PowerShell: .\mvnw.cmd clean compile
  - Or run the app: .\mvnw.cmd spring-boot:run

IntelliJ IDEA:
- Open the project and import as a Maven project.
- In the Maven tool window, run Lifecycle → clean, then compile.
- Or right-click com.dms.DmsBackendApplication and Run.

Troubleshooting: "package com.dms.dao does not exist" (and similar for dto/models/security)
- Cause: Running or compiling a single file instead of the Maven module.
- Fix:
  1) Ensure you’re in the dms-backend directory and run: mvn clean compile (or .\mvnw.cmd clean compile on Windows).
  2) In IntelliJ, do not use a "Run file" configuration. Use Maven or run the Spring Boot application class.
  3) Reimport Maven project and make sure src\\main\\java is marked as Sources Root.

If issues persist, share the exact command and full error output so we can help further.
