import { useState } from 'react'
import { carImageUrl } from '../../cars-api'

export function CarPhoto({
  carId,
  imageId,
  label,
}: {
  carId: string
  imageId: string
  label: string
}) {
  const [failed, setFailed] = useState(false)
  const url = carImageUrl(carId, imageId)
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-slate-50">
      {failed ? (
        <div className="flex aspect-[4/3] flex-col items-center justify-center gap-3 p-4">
          <p role="status">Could not load {label}.</p>
          <button className="btn-secondary" onClick={() => setFailed(false)}>
            Retry {label}
          </button>
        </div>
      ) : (
        <a href={url} target="_blank" rel="noreferrer" aria-label={`Open ${label}`}>
          <img
            className="aspect-[4/3] w-full object-contain"
            src={url}
            alt={label}
            loading="lazy"
            decoding="async"
            onError={() => setFailed(true)}
          />
        </a>
      )}
    </div>
  )
}
