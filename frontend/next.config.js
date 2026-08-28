/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Emits .next/standalone with only the modules actually imported, so the
  // runtime image does not carry node_modules. Roughly 1.1GB down to ~200MB,
  // which matters on a small VM that is also running Postgres and the API.
  output: 'standalone',
  images: {
    domains: ['localhost'],
  },
  // Anything under /api that this app does not implement itself is forwarded to
  // the backend. These are afterFiles rewrites, so the route handlers in
  // src/app/api win and only unmatched paths are proxied.
  //
  // BACKEND_URL is read on the server at request time and never reaches the
  // browser, so the same image runs behind any hostname.
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/api/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;