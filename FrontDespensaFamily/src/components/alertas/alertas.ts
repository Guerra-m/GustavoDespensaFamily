import './alertas.css';
import { esc } from '../../utils/format';

export function showAlert(message: string, type: 'success' | 'error' | 'info' = 'info', duration = 3000) {
  const container = document.getElementById('alert-container');
  if (!container) return;

  const alertDiv = document.createElement('div');
  alertDiv.className = `alert alert-${type}`;
  // DF-SEC-001: defensa en profundidad — hoy los llamadores pasan texto fijo,
  // pero algunos mensajes de error incluyen datos del servidor (ver
  // GlobalExceptionHandler), asi que se escapa igual antes de innerHTML.
  alertDiv.innerHTML = `
    <span>${esc(message)}</span>
    <button>&times;</button>
  `;

  alertDiv.querySelector('button')!.addEventListener('click', () => {
    container.removeChild(alertDiv);
  });

  container.appendChild(alertDiv);

  setTimeout(() => {
    if (container.contains(alertDiv)) container.removeChild(alertDiv);
  }, duration);
}

// src/components/alertas/alertas.ts
export function initAlertas() {
  if (!document.getElementById("alert-container")) {
    const container = document.createElement("div");
    container.id = "alert-container";
    container.className = "alert-container";
    document.body.appendChild(container);
  }
}
