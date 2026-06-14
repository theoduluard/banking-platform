import { Outlet } from 'react-router-dom'
import Navbar from './Navbar'

export default function Layout() {
  return (
    // flex-col on mobile: [top bar] then [content]
    // md:flex-row on desktop: [sidebar] beside [content]
    // h-screen + overflow-y-auto on main prevents double scrollbar
    <div className="flex h-screen flex-col md:flex-row bg-background">
      <Navbar />
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-5xl px-4 py-5 sm:px-6 sm:py-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
