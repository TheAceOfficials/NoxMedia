import DefaultTheme from 'vitepress/theme'
import './custom.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app, router }) {
    if (typeof window !== 'undefined') {
      // Initialize focus sections observer after DOM updates
      const setupFocusObserver = () => {
        setTimeout(() => {
          const sections = document.querySelectorAll('.doc-section')
          if (!sections || sections.length === 0) return

          // Set first section focused by default
          sections[0].classList.add('is-focused')

          const observer = new IntersectionObserver(
            (entries) => {
              entries.forEach((entry) => {
                if (entry.isIntersecting) {
                  sections.forEach((sec) => sec.classList.remove('is-focused'))
                  entry.target.classList.add('is-focused')
                }
              })
            },
            {
              root: null,
              rootMargin: '-20% 0px -55% 0px',
              threshold: 0.1
            }
          )

          sections.forEach((section) => observer.observe(section))
        }, 150)
      }

      window.addEventListener('DOMContentLoaded', setupFocusObserver)
      if (router) {
        router.onAfterRouteChanged = setupFocusObserver
      }
    }
  }
}
