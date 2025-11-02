import Link from 'next/link';

export default function Footer() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="bg-gray-900 text-gray-300 mt-auto">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Company Info */}
          <div>
            <h3 className="text-white font-semibold text-lg mb-3">Resume Buddy</h3>
            <p className="text-sm text-gray-400">
              AI-powered resume enhancement platform helping you land your dream job.
            </p>
          </div>

          {/* Legal Links */}
          <div>
            <h3 className="text-white font-semibold text-lg mb-3">Legal</h3>
            <ul className="space-y-2 text-sm">
              <li>
                <Link
                  href="/legal/terms"
                  className="hover:text-white transition-colors"
                >
                  Terms of Service
                </Link>
              </li>
              <li>
                <Link
                  href="/legal/privacy"
                  className="hover:text-white transition-colors"
                >
                  Privacy Notice
                </Link>
              </li>
            </ul>
          </div>

          {/* Contact */}
          <div>
            <h3 className="text-white font-semibold text-lg mb-3">Contact</h3>
            <ul className="space-y-2 text-sm text-gray-400">
              <li>
                <a
                  href="mailto:support@resumebuddy.cv"
                  className="hover:text-white transition-colors"
                >
                  support@resumebuddy.cv
                </a>
              </li>
              <li>
                <a
                  href="mailto:privacy@resumebuddy.cv"
                  className="hover:text-white transition-colors"
                >
                  privacy@resumebuddy.cv
                </a>
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="mt-8 pt-6 border-t border-gray-800 text-center text-sm text-gray-400">
          <p>© {currentYear} Resume Buddy. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}
