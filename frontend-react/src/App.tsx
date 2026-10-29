import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth, RequireModule } from './auth/guards'
import { ToastProvider } from './components/Toasts'
import { AppLayout } from './layout/AppLayout'
import { allMenuItems } from './layout/menu'
import { DashboardPage } from './pages/DashboardPage'
import { ItemsPage } from './pages/inventory/ItemsPage'
import { AssetsPage } from './pages/assets/AssetsPage'
import { DisposalPage } from './pages/assets/DisposalPage'
import { GatePassPage } from './pages/assets/GatePassPage'
import { MyAssetsPage } from './pages/assets/MyAssetsPage'
import { AdjustmentsPage } from './pages/issuance/AdjustmentsPage'
import { IssuancePage } from './pages/issuance/IssuancePage'
import { SemiExpendablePage } from './pages/issuance/SemiExpendablePage'
import { WarehousePage } from './pages/inventory/WarehousePage'
import { PlanningPage } from './pages/procurement/PlanningPage'
import { RfqPage } from './pages/rfq/RfqPage'
import { BiddingPage } from './pages/bidding/BiddingPage'
import { PurchaseOrdersPage } from './pages/purchasing/PurchaseOrdersPage'
import { ReceivingPage } from './pages/purchasing/ReceivingPage'
import { ContractsPage } from './pages/contracts/ContractsPage'
import { SuppliersPage } from './pages/suppliers/SuppliersPage'
import { PhysicalCountPage } from './pages/physical/PhysicalCountPage'
import { SettingsPage } from './pages/settings/SettingsPage'
import { AuditPage } from './pages/audit/AuditPage'
import { LogTrailsPage } from './pages/log-trails/LogTrailsPage'
import { BacPage } from './pages/bac/BacPage'
import { PurchaseRequestsPage } from './pages/procurement/PurchaseRequestsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { NotPortedPage } from './pages/NotPortedPage'
import { UsersPage } from './pages/users/UsersPage'

/** Pages that have moved to React, by path. Everything else in the menu shows a link to the old app. */
const ported: Record<string, React.ReactNode> = {
  '/dashboard': <DashboardPage />,
  '/users': <UsersPage />,
  '/items': <ItemsPage />,
  '/warehouse': <WarehousePage />,
  '/requests': <IssuancePage />,
  '/stock-adjustments': <AdjustmentsPage />,
  '/semi-expendable': <SemiExpendablePage />,
  '/properties': <AssetsPage />,
  '/my-assets': <MyAssetsPage />,
  '/disposal': <DisposalPage />,
  '/gate-pass': <GatePassPage />,
  '/procurement-planning': <PlanningPage />,
  '/purchase-requests': <PurchaseRequestsPage />,
  '/rfq': <RfqPage />,
  '/bidding': <BiddingPage />,
  '/procurement': <PurchaseOrdersPage />,
  '/receiving': <ReceivingPage />,
  '/contracts': <ContractsPage />,
  '/suppliers': <SuppliersPage />,
  '/bac': <BacPage />,
  '/physical': <PhysicalCountPage />,
  '/settings': <SettingsPage />,
  '/audit': <AuditPage />,
  '/log-trails': <LogTrailsPage />,
}

export function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <RequireAuth>
                  <AppLayout />
                </RequireAuth>
              }
            >
              <Route index element={<Navigate to="/dashboard" replace />} />
              {allMenuItems.map((item) => (
                <Route
                  key={item.path}
                  path={item.path}
                  element={<RequireModule module={item.module}>{ported[item.path] ?? <NotPortedPage item={item} />}</RequireModule>}
                />
              ))}
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
