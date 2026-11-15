import { createBrowserRouter, Navigate, Outlet, redirect } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { AppShell } from './AppShell'
import { GuestOnly } from './GuestOnly'
import { MissionRoute } from './MissionRoute'
import {
  DashboardPage,
  ForgotPasswordPage,
  LoadoutPage,
  LoginPage,
  MissionBriefingPage,
  MissionFieldPage,
  MissionPracticePage,
  MissionTrainingPage,
  OperationsPage,
  ProfilePage,
  RegisterPage,
  SettingsPage,
  TrainingAcademyPage,
} from './pages'
import { legacyAppRedirect, paths } from './paths'
import { RequireAuth } from './RequireAuth'

function RootLayout() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}

/**
 * React Router tree. Field room sits outside AppShell; other mission pages use the HQ chrome.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <Navigate to={paths.dashboard} replace /> },
      {
        path: 'app/*',
        loader: ({ request }) => {
          const url = new URL(request.url)
          const target = legacyAppRedirect(url.pathname) ?? paths.dashboard
          return redirect(target)
        },
        element: null,
      },
      {
        element: <GuestOnly />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
          { path: 'forgot-password', element: <ForgotPasswordPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            path: 'missions/:missionId/field',
            element: <MissionRoute />,
            children: [{ index: true, element: <MissionFieldPage /> }],
          },
          {
            element: <AppShell />,
            children: [
              { path: 'dashboard', element: <DashboardPage /> },
              { path: 'operations', element: <OperationsPage /> },
              { path: 'training-academy', element: <TrainingAcademyPage /> },
              { path: 'profile', element: <ProfilePage /> },
              { path: 'loadout', element: <LoadoutPage /> },
              { path: 'settings', element: <SettingsPage /> },
              {
                path: 'missions/:missionId',
                element: <MissionRoute />,
                children: [
                  { index: true, element: <Navigate to="briefing" replace /> },
                  { path: 'briefing', element: <MissionBriefingPage /> },
                  { path: 'training', element: <MissionTrainingPage /> },
                  { path: 'practice', element: <MissionPracticePage /> },
                ],
              },
            ],
          },
        ],
      },
      { path: '*', element: <Navigate to={paths.dashboard} replace /> },
    ],
  },
])
