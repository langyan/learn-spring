import { useMemo, useState } from 'react'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8086'

const createInitialForm = () => ({
  customerId: 'user-001',
  productName: 'Mechanical Keyboard',
  quantity: 1,
})

const createIdempotencyKey = () =>
  `idem-${Math.random().toString(36).slice(2, 8)}-${Date.now().toString(36)}`

async function parseResponse(response) {
  const text = await response.text()
  const payload = text ? JSON.parse(text) : {}
  if (!response.ok) {
    throw new Error(payload.message || `Request failed with status ${response.status}`)
  }
  return payload
}

export default function App() {
  const [form, setForm] = useState(createInitialForm)
  const [idempotencyKey, setIdempotencyKey] = useState(createIdempotencyKey)
  const [lastResponse, setLastResponse] = useState(null)
  const [rewardRecords, setRewardRecords] = useState([])
  const [logLines, setLogLines] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const orderNo = useMemo(() => lastResponse?.orderNo || '', [lastResponse])

  const appendLog = (message) => {
    setLogLines((current) => [`${new Date().toLocaleTimeString()} ${message}`, ...current].slice(0, 8))
  }

  const handleChange = (field, value) => {
    setForm((current) => ({
      ...current,
      [field]: field === 'quantity' ? Number(value) : value,
    }))
  }

  const submitOrder = async (keyToUse) => {
    const response = await fetch(`${API_BASE_URL}/api/orders`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': keyToUse,
      },
      body: JSON.stringify(form),
    })
    return parseResponse(response)
  }

  const loadRewardRecords = async (targetOrderNo) => {
    if (!targetOrderNo) {
      return
    }
    const records = await parseResponse(
      await fetch(`${API_BASE_URL}/api/orders/${targetOrderNo}/reward-records`),
    )
    setRewardRecords(records)
    appendLog(`Loaded ${records.length} consumer record(s) for ${targetOrderNo}`)
  }

  const handleCreateOrder = async () => {
    setLoading(true)
    setError('')
    try {
      const payload = await submitOrder(idempotencyKey)
      setLastResponse(payload)
      appendLog(`Submitted order with key ${idempotencyKey}`)
      if (payload.orderNo) {
        await loadRewardRecords(payload.orderNo)
      }
    } catch (requestError) {
      setError(requestError.message)
      appendLog(`Create order failed: ${requestError.message}`)
    } finally {
      setLoading(false)
    }
  }

  const handleDuplicateSubmit = async () => {
    setLoading(true)
    setError('')
    try {
      const first = await submitOrder(idempotencyKey)
      const second = await submitOrder(idempotencyKey)
      setLastResponse(second)
      appendLog(`First request created ${first.orderNo || 'processing result'}`)
      appendLog('Second request reused the same Idempotency-Key')
      if (second.orderNo) {
        await loadRewardRecords(second.orderNo)
      }
    } catch (requestError) {
      setError(requestError.message)
      appendLog(`Duplicate submit failed: ${requestError.message}`)
    } finally {
      setLoading(false)
    }
  }

  const handleFetchOrder = async () => {
    if (!orderNo) {
      setError('Create an order first to fetch it again')
      return
    }
    setLoading(true)
    setError('')
    try {
      const payload = await parseResponse(await fetch(`${API_BASE_URL}/api/orders/${orderNo}`))
      setLastResponse(payload)
      appendLog(`Fetched order ${orderNo} from backend`)
      await loadRewardRecords(orderNo)
    } catch (requestError) {
      setError(requestError.message)
      appendLog(`Fetch order failed: ${requestError.message}`)
    } finally {
      setLoading(false)
    }
  }

  const handleReplayEvent = async () => {
    if (!orderNo) {
      setError('Create an order first to replay its Kafka event')
      return
    }
    setLoading(true)
    setError('')
    try {
      const payload = await parseResponse(
        await fetch(`${API_BASE_URL}/api/orders/${orderNo}/events/replay`, {
          method: 'POST',
        }),
      )
      appendLog(`Replayed Kafka event ${payload.eventId}`)
      await loadRewardRecords(orderNo)
    } catch (requestError) {
      setError(requestError.message)
      appendLog(`Replay event failed: ${requestError.message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <header className="hero">
        <p className="eyebrow">React + Spring Boot + Redis + Kafka</p>
        <h1>设计分布式系统中的幂等性</h1>
        <p className="summary">
          这个页面同时演示 API 请求幂等和 Kafka 消费幂等。用相同的
          <code>Idempotency-Key</code>
          重复提交，只会创建一个订单；重放同一个 Kafka 事件，也只会生成一条消费记录。
        </p>
      </header>

      <main className="layout">
        <section className="card">
          <h2>下单请求</h2>
          <label>
            Customer ID
            <input
              value={form.customerId}
              onChange={(event) => handleChange('customerId', event.target.value)}
            />
          </label>
          <label>
            Product Name
            <input
              value={form.productName}
              onChange={(event) => handleChange('productName', event.target.value)}
            />
          </label>
          <label>
            Quantity
            <input
              type="number"
              min="1"
              value={form.quantity}
              onChange={(event) => handleChange('quantity', event.target.value)}
            />
          </label>
          <label>
            Idempotency-Key
            <div className="inline">
              <input
                value={idempotencyKey}
                onChange={(event) => setIdempotencyKey(event.target.value)}
              />
              <button type="button" onClick={() => setIdempotencyKey(createIdempotencyKey())}>
                生成新 Key
              </button>
            </div>
          </label>
          <div className="actions">
            <button type="button" onClick={handleCreateOrder} disabled={loading}>
              提交一次
            </button>
            <button type="button" onClick={handleDuplicateSubmit} disabled={loading}>
              用同一 Key 连续提交两次
            </button>
          </div>
          <div className="actions">
            <button type="button" onClick={handleFetchOrder} disabled={loading}>
              查询最后一个订单
            </button>
            <button type="button" onClick={handleReplayEvent} disabled={loading}>
              重放同一个 Kafka 事件
            </button>
          </div>
          {error ? <p className="error">{error}</p> : null}
        </section>

        <section className="card">
          <h2>最后一次接口响应</h2>
          <pre>{JSON.stringify(lastResponse, null, 2)}</pre>
        </section>

        <section className="card">
          <h2>Kafka 消费记录</h2>
          <pre>{JSON.stringify(rewardRecords, null, 2)}</pre>
        </section>

        <section className="card">
          <h2>调试日志</h2>
          <ul className="logList">
            {logLines.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </section>
      </main>
    </div>
  )
}
