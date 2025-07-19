# Review System Microservice

A Spring Boot microservice for processing hotel reviews from S3 bucket.

## Requirements
- **Java:** 17.0.15
- **Maven:** 3.6+

## Pre-commit Hooks (Automated Quality Gates)

This project uses Maven to manage and install Git pre-commit hooks for code quality and reliability. No extra tools or manual copying required—just Maven!

### How it Works
- The `hooks/pre-commit` script is versioned in the repo.
- On `mvn install`, the script is automatically installed to `.git/hooks/pre-commit` and made executable.
- On every commit, the following checks run:
  - Code formatting (Spotless)
  - Linting (Checkstyle)
  - Static analysis (PMD)
  - Unit tests
  - Code coverage report (JaCoCo)
- If any check fails, the commit is blocked with a helpful message.

### Team Setup Instructions

1. **Clone the repository:**
   ```sh
   git clone <repo-url>
   cd <repo-directory>
   ```
2. **Install the Git hooks:**
   ```sh
   mvn install
   ```
   This will set up the pre-commit hook for you.
3. **Update hooks after changes:**
   If the `hooks/pre-commit` script changes, run `mvn install` again to update your local hook.
4. **Troubleshooting:**
   If you get a permissions error, run:
   ```sh
   chmod +x .git/hooks/pre-commit
   ```

## Project Structure
- `src/main/java/` - Application source code
- `src/test/java/` - Test code
- `hooks/pre-commit` - Versioned pre-commit hook script
