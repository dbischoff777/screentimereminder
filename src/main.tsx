import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/index.css'
import './styles/DropdownUtils.css'
import './styles/TabStyles.css'
import './utils/dropdownPositioningFix'
import './styles/FocusTimerPage.css'
import App from './components/App.tsx'
import { ScreenTimeProvider } from './context/ScreenTimeContext.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ScreenTimeProvider>
      <App />
    </ScreenTimeProvider>
  </StrictMode>,
)
