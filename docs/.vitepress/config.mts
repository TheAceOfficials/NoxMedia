import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "NoxMedia",
  description: "Next-Generation Media Server for Telegram, Infuse, Stremio & Nuvio",
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
          text: '🚀 Getting Started',
          items: [
            { text: 'Introduction', link: '/guide/getting-started' },
            { text: 'Telegram Channels Setup', link: '/guide/channels-setup' },
            { text: 'AI & Custom Shelves', link: '/guide/ai-catalogs' },
            { text: 'True Cloud Sync & Standby', link: '/guide/cloud-sync' }
          ]
        },
        {
          text: '📱 Clients & Compatibility',
          items: [
            { text: '🍎 Infuse (Apple TV & iOS)', link: '/clients/infuse' },
            { text: '🍿 Stremio & Nuvio', link: '/clients/stremio-nuvio' },
            { text: '📺 Android TV & Kodi', link: '/clients/android-tv-kodi' }
          ]
        }
      ],
      '/clients/': [
        {
          text: '📱 Clients & Compatibility',
          items: [
            { text: '🍎 Infuse (Apple TV & iOS)', link: '/clients/infuse' },
            { text: '🍿 Stremio & Nuvio', link: '/clients/stremio-nuvio' },
            { text: '📺 Android TV & Kodi', link: '/clients/android-tv-kodi' }
          ]
        },
        {
          text: '🚀 Getting Started',
          items: [
            { text: 'Introduction', link: '/guide/getting-started' },
            { text: 'Telegram Channels Setup', link: '/guide/channels-setup' },
            { text: 'AI & Custom Shelves', link: '/guide/ai-catalogs' }
          ]
        }
      ],
      '/updates/': [
        {
          text: '🚀 Releases & Changelog',
          items: [
            { text: 'All Updates Overview', link: '/updates/' },
            { text: 'v1.4.325 • Infuse Custom Shelves', link: '/updates/v1-4-325' },
            { text: 'v1.4.320 • AI Catalog Curation', link: '/updates/v1-4-320' },
            { text: 'v1.4.318 • Runtime & Metadata Fallbacks', link: '/updates/v1-4-318' }
          ]
        }
      ]
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
      message: 'Crafted with passion for cinema lovers & media collectors.',
      copyright: 'Copyright © 2026 TheAceOfficials • NoxMedia'
    }
  }
})
