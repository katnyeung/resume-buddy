"use client";

import { usePathname } from 'next/navigation';
import Footer from './Footer';

export default function ConditionalFooter() {
  const pathname = usePathname();

  // Hide footer on interview pages
  if (pathname?.startsWith('/interview')) {
    return null;
  }

  return <Footer />;
}
