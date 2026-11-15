import { RouterProvider } from 'react-router-dom'
import { router } from './routes/router'
import './App.css'
import './theme/spyCinema.css'
import './theme/fieldRoom.css'
import './theme/terminalScreen.css'

function App() {
  return <RouterProvider router={router} />
}

export default App
