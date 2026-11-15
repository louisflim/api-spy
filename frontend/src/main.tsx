import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import './index.css'

// StrictMode is intentionally omitted while Phaser owns a WebGL canvas.
// Double-mounting in development destroys the game context mid-boot.
createRoot(document.getElementById('root')!).render(<App />)
