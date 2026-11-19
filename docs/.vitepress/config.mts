import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "NoxMedia",
  description: "Next-Generation Media Server for Telegram, Infuse, Vesper & Nuvio",
  base: '/NoxMedia/',
  cleanUrls: true,
  lastUpdated: true,

  head: [
    ['link', { rel: 'icon', href: '/NoxMedia/logo.png' }],
    ['meta', { name: 'theme-color', content: '#09090b' }],
    ['meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }],
    ['meta', { name: 'apple-mobile-web-app-status-bar-style', content: 'black-translucent' }]
  ],

  themeConfig: {
    logo: '/logo.png',
    siteTitle: 'NoxMedia',

    nav: [
      { text: 'Documentation', link: '/guide/getting-started' },
      { text: 'Clients & Setup', link: '/clients/infuse' },
      { text: 'Roadmap', link: '/roadmap' },
      { text: "What's New", link: '/updates/' },
      { text: 'FAQ', link: '/faq' },
      {
        text: 'Download APK',
        link: 'https://github.com/TheAceOfficials/NoxMedia/releases/latest'
      }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Getting Started',
          items: [
            { text: 'Introduction', link: '/guide/getting-started' },
            { text: 'Telegram Channels Setup', link: '/guide/channels-setup' },
            { text: 'AI & Custom Shelves', link: '/guide/ai-catalogs' },
            { text: 'True Cloud Sync & Standby', link: '/guide/cloud-sync' },
            { text: 'Roadmap & Progress', link: '/roadmap' }
          ]
        },
        {
          text: 'Clients & Compatibility',
          items: [
            { text: 'Infuse & Vesper (Apple)', link: '/clients/infuse' },
            { text: 'Nuvio Streaming', link: '/clients/stremio-nuvio' },
            { text: 'Android TV & Kodi', link: '/clients/android-tv-kodi' }
          ]
        }
      ],
      '/clients/': [
        {
          text: 'Clients & Compatibility',
          items: [
            { text: 'Infuse & Vesper (Apple)', link: '/clients/infuse' },
            { text: 'Nuvio Streaming', link: '/clients/stremio-nuvio' },
            { text: 'Android TV & Kodi', link: '/clients/android-tv-kodi' }
          ]
        },
        {
          text: 'Getting Started',
          items: [
            { text: 'Introduction', link: '/guide/getting-started' },
            { text: 'Telegram Channels Setup', link: '/guide/channels-setup' },
            { text: 'AI & Custom Shelves', link: '/guide/ai-catalogs' },
            { text: 'Roadmap & Progress', link: '/roadmap' }
          ]
        }
      ],
      '/updates/': [
        {
          text: 'Releases & Changelog',
          items: [
            { text: 'All Updates Overview', link: '/updates/' },
            { text: 'v1.4.325 - Infuse Custom Shelves', link: '/updates/v1-4-325' },
            { text: 'v1.4.320 - AI Catalog Curation', link: '/updates/v1-4-320' },
            { text: 'v1.4.318 - Runtime & Metadata Fallbacks', link: '/updates/v1-4-318' }
          ]
        }
      ],
      '/roadmap': [
        {
          text: 'Project Direction',
          items: [
            { text: 'Roadmap & Progress', link: '/roadmap' },
            { text: 'Documentation', link: '/guide/getting-started' },
            { text: 'Clients & Setup', link: '/clients/infuse' }
          ]
        }
      ]
    },

    outline: {
      level: [2, 3],
      label: 'On this page'
    },

    search: {
      provider: 'local',
      options: {
        detailedView: true
      }
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/TheAceOfficials/NoxMedia' }
    ],

    footer: {
      message: 'Crafted with precision for cinema lovers and personal media libraries.',
      copyright: 'Copyright (c) 2026 TheAceOfficials - NoxMedia'
    }
  }
})
