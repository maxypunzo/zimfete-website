import { Link, Route, Routes } from 'react-router-dom'

import { Layout } from './components/Layout'
import { Empty } from './components/ui'
import { ApplicationDetailPage } from './pages/ApplicationDetailPage'
import { ApplicationsPage } from './pages/ApplicationsPage'
import { AssetDetailPage } from './pages/AssetDetailPage'
import { AssetsPage } from './pages/AssetsPage'
import { CataloguePage } from './pages/CataloguePage'
import { DashboardPage } from './pages/DashboardPage'
import { NewApplicationPage } from './pages/NewApplicationPage'
import { PurchaseOrdersPage } from './pages/PurchaseOrdersPage'
import { QueuePage } from './pages/QueuePage'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/new" element={<NewApplicationPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="queue" element={<QueuePage />} />
        <Route path="purchase-orders" element={<PurchaseOrdersPage />} />
        <Route path="assets" element={<AssetsPage />} />
        <Route path="assets/:id" element={<AssetDetailPage />} />
        <Route path="catalogue" element={<CataloguePage />} />
        <Route path="*" element={<Empty>Page not found. <Link to="/">Go to the dashboard</Link></Empty>} />
      </Route>
    </Routes>
  )
}
