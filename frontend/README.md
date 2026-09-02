# Practice Debt — frontend

React + Vite + TypeScript, Tailwind v4, React Query. Components follow shadcn/ui conventions and
live in `src/components/ui` as ordinary source files, which is how shadcn is meant to be used.

```bash
npm install
npm run dev     # http://localhost:5173, proxies /api to the backend on :8080
npm run build
```

The backend must be running. The browser never talks to Codeforces directly — everything is served
from the local mirror, so the page stays fast and the API's rate limit is never a user-facing
concern.

The whole product is one list. There are no charts: tracking and visualisation are not the point,
and earn their place only insofar as they make the queue trustworthy. What
earns its place instead is the reason on every row, and the assumptions panel that says plainly
which numbers are measured and which are guesses.
