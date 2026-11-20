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
        'native-apps': 1,
        'platform-availability': 0,
        'active-priorities': 0,
        'completed-milestones': 0
      }

      let currentActiveSectionId = ''
      let currentActiveGroupIndex = -1
      let scrollTicking = false

      const setupScrollSpyAndAccordion = () => {
        currentActiveSectionId = ''
        currentActiveGroupIndex = -1

        setTimeout(() => {
          const sections = Array.from(document.querySelectorAll<HTMLElement>('.doc-section[id]'))
          if (!sections || sections.length === 0) return

          const updateActiveState = () => {
            const vh = window.innerHeight || document.documentElement.clientHeight
            // Reading line anchor: 35% from the top of the viewport
            const threshold = Math.min(260, vh * 0.35)

            // 1. Identify which section is currently active based on viewport rect
            let candidate: HTMLElement | null = null

            for (let i = 0; i < sections.length; i++) {
              const sec = sections[i]
              const rect = sec.getBoundingClientRect()
              if (rect.top <= threshold) {
                candidate = sec
              } else {
                break
              }
            }

            if (!candidate && sections.length > 0) {
              candidate = sections[0]
            }

            // Bottom of page check
            if ((window.innerHeight + window.scrollY) >= (document.documentElement.scrollHeight - 60)) {
              candidate = sections[sections.length - 1]
            }

            if (candidate) {
              const sectionId = candidate.getAttribute('id') || ''

              // 2. Light up focused section
              sections.forEach((sec) => {
                if (sec === candidate) {
                  sec.classList.add('is-focused')
                } else {
                  sec.classList.remove('is-focused')
                }
              })

              if (sectionId !== currentActiveSectionId) {
                currentActiveSectionId = sectionId

                // 3. Highlight EXACTLY ONE sidebar link (remove all others)
                const allSidebarItems = document.querySelectorAll<HTMLElement>('.VPSidebar .VPSidebarItem.level-1')
                let foundMatch = false

                allSidebarItems.forEach((item) => {
                  const link = item.querySelector('.link') as HTMLAnchorElement | null
                  const href = link?.getAttribute('href') || ''

                  if (!foundMatch && href.endsWith('#' + sectionId)) {
                    item.classList.add('is-current-scroll-active')
                    link?.classList.add('is-current-scroll-active')
                    foundMatch = true
                  } else {
                    item.classList.remove('is-current-scroll-active', 'is-active')
                    link?.classList.remove('is-current-scroll-active', 'is-active')
                  }
                })

                // 4. Smart Auto-Accordion
                const targetGroup = sectionGroupMap[sectionId]
                if (typeof targetGroup === 'number' && targetGroup !== currentActiveGroupIndex) {
                  currentActiveGroupIndex = targetGroup

                  const groups = document.querySelectorAll<HTMLElement>('.VPSidebar .VPSidebarItem.level-0.collapsible')
                  groups.forEach((group, idx) => {
                    const isCollapsed = group.classList.contains('collapsed')
                    const toggleBtn = (group.querySelector('.caret') || group.querySelector('.item')) as HTMLElement | null

                    if (idx === targetGroup) {
                      // Expand target group if collapsed
                      if (isCollapsed && toggleBtn) {
                        toggleBtn.click()
                      }
                    } else if (idx === 0 || idx === 1) {
                      // Collapse other group if expanded
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
