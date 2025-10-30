export default function NotFound() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="bg-white p-8 rounded-lg shadow-md max-w-md text-center">
        <div className="text-red-500 text-5xl mb-4">⚠️</div>
        <h1 className="text-2xl font-bold mb-4">Session Not Found</h1>
        <p className="text-gray-600 mb-6">
          The interview session you are looking for does not exist or has expired.
        </p>
        <a
          href="/"
          className="inline-block px-6 py-3 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Go to Home
        </a>
      </div>
    </div>
  );
}
