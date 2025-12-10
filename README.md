# DocGen: AI-Powered API Documentation Generator

DocGen is a modern, full-stack developer utility built using **Spring Boot**, **Google Gemini AI API**, **OpenPDF**, and **Tailwind CSS**. It auto-generates structured, interactive REST API documentation from standard Spring Boot Controller Java code, reducing manual documentation time to seconds.

## Features

- ⚡ **Instant Parse**: Extracts request mapping methods (`GET`, `POST`, `PUT`, `DELETE`), URL path mappings, headers, query parameters, and path variables.
- 🧠 **AI Schema & Mock Generation**: Automatically constructs mock request/response JSON objects matching the Java model signatures.
- 🎨 **Visual Dashboard**: A dark-themed dashboard to inspect and edit details (such as parameters, schemas, and status codes) in real-time.
- 📄 **PDF Export**: Generates clean, formatted PDF API reference manuals using OpenPDF.
- 🌐 **HTML Export**: Generates standalone, responsive, single-file HTML API manuals.
- 🐳 **Dockerized & Cloud Ready**: Complete Docker and Railway setup.

---

## Getting Started

### 1. Prerequisites

- **Java 17** (or 21) installed.
- **Gemini API Key**:
  1. Go to [Google AI Studio](https://aistudio.google.com/).
  2. Create a **free API key**.

### 2. Configure Environment Variable

Set the environment variable on your system:

#### Windows (Command Prompt):
```cmd
set GEMINI_API_KEY=your_gemini_api_key_here
```

#### Windows (PowerShell):
```powershell
$env:GEMINI_API_KEY="your_gemini_api_key_here"
```

#### Linux/macOS:
```bash
export GEMINI_API_KEY="your_gemini_api_key_here"
```

---

## How to Run Locally

Since this is a standard Maven project, you can load the root folder directly in your IDE (IntelliJ IDEA, Eclipse, Spring Tool Suite, or VS Code). The IDE will resolve dependencies automatically.

### Running from the Terminal

If you have Maven installed, run:
```bash
mvn spring-boot:run
```

Once running, open your browser and navigate to:
```
http://localhost:8080
```

---

## Deployment (Docker & Railway)

The project includes a ready-to-use Dockerfile. To deploy it to Railway:

1. Install the Railway CLI or link the project to your GitHub repository.
2. In your Railway dashboard, add `GEMINI_API_KEY` under the service's **Variables** tab.
3. Deploy! Railway will automatically detect the Dockerfile, build, and deploy the application.
