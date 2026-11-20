import DefaultTheme from 'vitepress/theme'
import './custom.css'
import DeviceIcon from './DeviceIcon.vue'
import DeviceGroup from './DeviceGroup.vue'
import HomePage from './HomePage.vue'

export default {
  extends: DefaultTheme,
  enhanceApp({ app, router }) {
    app.component('DeviceIcon', DeviceIcon)
    app.component('DeviceGroup', DeviceGroup)
    app.component('HomePage', HomePage)

    if (typeof window !== 'undefined') {
      const sectionGroupMap: Record<string, number> = {
        'introduction': 0,
        'architecture': 0,
        'installation': 0,
        'channels-setup': 0,
        'ai-catalogs': 0,
        'cloud-sync': 0,
        'nuvio': 1,
        'jellyfin-bridge': 1,
        'native-apps': 1
      }

      let currentActiveSectionId = ''
      let currentActiveGroupIndex = -1
      let scrollTicking = false

      const setupScrollSpyAndAccordion = () => {
        setTimeout(() => {
          const sections = Array.from(document.querySelectorAll<HTMLElement>('.doc-section[id]'))
          if (!sections || sections.length === 0) return

          const updateActiveState = () => {
            const scrollPos = window.scrollY + 140

            // Find current section in view
            let activeSection: HTMLElement | null = null
            for (let i = sections.length - 1; i >= 0; i--) {
              const sec = sections[i]
              if (sec.offsetTop <= scrollPos) {
                activeSection = sec
                break
              }
            }

            if (!activeSection && sections.length > 0) {
              activeSection = sections[0]
            }

            if (activeSection) {
              const sectionId = activeSection.getAttribute('id') || ''

              // 1. Focus dimming (opacity focus)
              sections.forEach((sec) => {
                if (sec === activeSection) {
                  sec.classList.add('is-focused')
                } else {
                  sec.classList.remove('is-focused')
                }
              })

              if (sectionId !== currentActiveSectionId) {
                currentActiveSectionId = sectionId

                // 2. Realtime sidebar link highlight
                const sidebarLinks = document.querySelectorAll<HTMLElement>('.VPSidebar .VPLink')
                sidebarLinks.forEach((link) => {
                  const href = link.getAttribute('href') || ''
                  const item = link.closest('.VPSidebarItem')
                  if (href.endsWith('#' + sectionId)) {
                    link.classList.add('is-active')
                    item?.classList.add('is-active')
                  } else {
                    link.classList.remove('is-active')
                    item?.classList.remove('is-active')
                  }
                })

                // 3. Smart Auto-Accordion: switch expanded group when crossing boundaries
                const targetGroup = sectionGroupMap[sectionId]
                if (typeof targetGroup === 'number' && targetGroup !== currentActiveGroupIndex) {
                  currentActiveGroupIndex = targetGroup

                  const groups = document.querySelectorAll<HTMLElement>('.VPSidebar .VPSidebarItem.level-0.collapsible')
                  groups.forEach((group, idx) => {
                    const isCollapsed = group.classList.contains('collapsed')
                    const toggleBtn = (group.querySelector('.caret') || group.querySelector('.item')) as HTMLElement | null

                    if (idx === targetGroup) {
                      // Expand target group if currently collapsed
                      if (isCollapsed && toggleBtn) {
                        toggleBtn.click()
                      }
                    } else if (idx === 0 || idx === 1) {
                      // Collapse other group if currently expanded
                      if (!isCollapsed && toggleBtn) {
                        toggleBtn.click()
                      }
                    }
                  })
                }
              }
            }
          }

          const onScroll = () => {
            if (!scrollTicking) {
              requestAnimationFrame(() => {
                updateActiveState()
                scrollTicking = false
              })
              scrollTicking = true
            }
          }

          window.removeEventListener('scroll', onScroll)
          window.addEventListener('scroll', onScroll, { passive: true })

          // Initial run
          updateActiveState()
        }, 150)
      }

      window.addEventListener('DOMContentLoaded', setupScrollSpyAndAccordion)
      if (router) {
        router.onAfterRouteChanged = setupScrollSpyAndAccordion
      }
    }
  }
}
