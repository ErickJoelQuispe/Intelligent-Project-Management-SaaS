module.exports = {
  ci: {
    collect: {
      url: ['http://localhost:4200'],
      numberOfRuns: 1,
      settings: {
        // Skip service worker for CI (no HTTPS)
        skipAudits: ['service-worker', 'installable-manifest'],
      },
    },
    assert: {
      assertions: {
        'categories:performance': ['warn', { minScore: 0.8 }],
        'categories:accessibility': ['error', { minScore: 0.9 }],
        'categories:best-practices': ['warn', { minScore: 0.8 }],
        'categories:seo': ['warn', { minScore: 0.8 }],
      },
    },
    upload: {
      target: 'temporary-public-storage',
    },
  },
};
