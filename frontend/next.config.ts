import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone', // Required for Docker deployment
  reactStrictMode: false, // Disable for interview WebSocket (causes double mount/unmount)
  eslint: {
    // Disable ESLint during production build for MVP
    // TODO: Fix all lint errors before production
    ignoreDuringBuilds: true,
  },
  typescript: {
    // Keep TypeScript checking enabled
    ignoreBuildErrors: false,
  },
};

export default nextConfig;
