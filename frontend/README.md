# ComplyAI Frontend

React/Next.js frontend for the ComplyAI Security Questionnaire Automation platform.

## Features

- **Dashboard** - Overview of questionnaires, documents, and team activity
- **Questionnaire Management** - Create, process, and track security questionnaires
- **Side-by-Side Review UI** - Human-in-the-loop review with approve/edit/reject workflow
- **Document Management** - Upload and manage knowledge base documents
- **Integrations** - Configure Jira, Confluence, GitHub, and Slack integrations
- **Settings** - Appearance, notifications, and API access

## Tech Stack

- **Framework**: Next.js 14 (App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **State Management**: TanStack Query (React Query) + Zustand
- **Forms**: React Hook Form + Zod
- **Authentication**: Keycloak OIDC integration
- **UI Components**: Custom components with Lucide icons

## Getting Started

### Prerequisites

- Node.js 18+
- npm or yarn
- Backend API running on `http://localhost:8080`
- Keycloak running on `http://localhost:8081`

### Installation

```bash
cd frontend
npm install
```

### Development

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Build

```bash
npm run build
npm start
```

### Linting

```bash
npm run lint
```

### Type Checking

```bash
npm run type-check
```

## Project Structure

```
frontend/
├── src/
│   ├── app/                    # Next.js App Router pages
│   │   ├── dashboard/          # Protected dashboard routes
│   │   │   ├── layout.tsx      # Dashboard layout with sidebar
│   │   │   ├── page.tsx        # Dashboard home
│   │   │   ├── questionnaires/ # Questionnaire management
│   │   │   ├── documents/      # Document management
│   │   │   └── settings/       # Settings & integrations
│   │   ├── login/              # Login page
│   │   ├── globals.css         # Global styles
│   │   └── layout.tsx          # Root layout
│   ├── components/
│   │   ├── ui/                 # Reusable UI components
│   │   └── review/             # Review-specific components
│   ├── hooks/                  # Custom React hooks
│   ├── lib/                    # Utilities and API client
│   └── types/                  # TypeScript types
├── public/                     # Static assets
├── package.json
├── tsconfig.json
├── tailwind.config.ts
└── next.config.js
```

## Key Components

### QuestionnaireReview
The main review component featuring:
- Side-by-side question and answer view
- AI-generated answers with citations
- Approve/Edit/Reject workflow
- Progress tracking
- Export functionality (Excel, PDF, Word)

### QuestionCard
Individual question card with:
- Question metadata (type, required, section)
- AI-generated answer display
- Citation panel with relevance scores
- Inline editing
- Status badges

### Integrations Configuration
Settings page for configuring:
- **Jira**: Create issues and sub-tasks
- **Confluence**: Publish pages
- **GitHub**: Create issues
- **Slack**: Notifications and approvals

## Authentication

The frontend integrates with Keycloak for authentication:
- OIDC Authorization Code flow
- Token management with automatic refresh
- Protected routes with automatic redirect to login

## API Integration

The API client (`src/lib/api.ts`) provides:
- Axios instance with interceptors
- Automatic token attachment
- Error handling with auth redirect
- Typed methods for all endpoints

## Deployment

### Docker

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
EXPOSE 3000
CMD ["npm", "start"]
```

### Environment Variables

Copy `.env.example` to `.env.local` and configure:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_KEYCLOAK_URL=http://localhost:8081
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=complyai-frontend
NEXT_PUBLIC_KEYCLOAK_REALM=complyai
```

## Contributing

1. Follow the existing code style
2. Run `npm run lint` and `npm run type-check` before committing
3. Write tests for new features
4. Update documentation as needed

## License

Proprietary - Internal project