import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ApiError } from "../../api/client";
import { planningApi, type AppLineInput } from "../../api/endpoints";
import type { AppLine, AppPlan, ProcurementOptions } from "../../api/types";
import { ConfirmDialog, Modal } from "../../components/Modal";
import { formatDate, formatPeso, formatQty } from "../../lib/format";
import { Badge } from "../assets/pickers";
import { APP_STATUS, LINE_STATUS, QUARTER_LABEL } from "./labels";

interface Props {
  appId: number;
  options?: ProcurementOptions;
  onClose: () => void;
  onChanged: (message: string, closed?: boolean) => void;
}

export function Meter({ rate }: { rate: number }) {
  return (
    <span className="meter" title={`${rate}%`}>
      <span style={{ width: `${Math.min(100, rate)}%` }} />
    </span>
  );
}

export function AppDetailModal({ appId, options, onClose, onChanged }: Props) {
  const q = useQuery({
    queryKey: ["app", appId],
    queryFn: () => planningApi.app(appId),
  });
  const [edit, setEdit] = useState<Record<number, AppLineInput> | null>(null);
  const [title, setTitle] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fail = (err: unknown) =>
    setError(err instanceof ApiError ? err.message : "Something went wrong.");
  const a = q.data;

  const save = useMutation({
    mutationFn: () =>
      planningApi.updateApp(appId, { title, items: Object.values(edit!) }),
    onSuccess: (r) => {
      setEdit(null);
      onChanged(`${r.app_number} saved.`);
    },
    onError: fail,
  });
  const approve = useMutation({
    mutationFn: () => planningApi.approveApp(appId),
    onSuccess: (r) =>
      onChanged(
        `${r.app_number} approved; the schedule was sent to procurement.`,
      ),
    onError: fail,
  });
  const regen = useMutation({
    mutationFn: () => planningApi.generateApp(a!.fiscal_year),
    onSuccess: (r) =>
      onChanged(`${r.app_number} now has ${r.items_count} lines.`),
    onError: fail,
  });
  const remove = useMutation({
    mutationFn: () => planningApi.deleteApp(appId),
    onSuccess: (r) => onChanged(r.message, true),
    onError: (e) => {
      setDeleting(false);
      fail(e);
    },
  });

  const startEdit = (app: AppPlan) => {
    setTitle(app.title);
    setEdit(
      Object.fromEntries(
        (app.items ?? []).map((l) => [
          l.id,
          {
            id: l.id,
            general_description: l.general_description,
            specifications: l.specifications,
            unit: l.unit,
            procurement_mode: l.procurement_mode,
            procurement_category: l.procurement_category,
            quantity: l.quantity,
            estimated_budget: l.estimated_budget,
            schedule: l.schedule,
            fund_source: l.fund_source,
          },
        ]),
      ),
    );
  };
  const patch = (id: number, p: Partial<AppLineInput>) =>
    setEdit((e) => ({ ...e!, [id]: { ...e![id], ...p } }));
  const busy = save.isPending || approve.isPending || regen.isPending;

  return (
    <Modal
      title={
        a ? `${a.app_number} — FY ${a.fiscal_year}` : "Annual Procurement Plan"
      }
      onClose={onClose}
      width={1100}
    >
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && (
        <div className="alert alert-error">{(q.error as Error).message}</div>
      )}
      {a && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          {a.pending_ppmps > 0 && a.can_edit && (
            <div className="alert alert-warning">
              {a.pending_ppmps} approved PPMP(s) for FY {a.fiscal_year} are not
              in this APP yet.{" "}
              <button
                className="btn btn-outline btn-sm"
                onClick={() => regen.mutate()}
                disabled={busy}
              >
                {regen.isPending ? "Adding…" : "Reconsolidate"}
              </button>
            </div>
          )}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={APP_STATUS} value={a.status} />{" "}
              <span className="muted">
                {a.ppmp_count} PPMP(s), {a.items_count} lines
              </span>
            </dd>
            <dt>Title</dt>
            <dd>
              {edit ? (
                <input
                  aria-label="APP title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={255}
                />
              ) : (
                a.title
              )}
            </dd>
            <dt>Consolidated by</dt>
            <dd>
              {a.preparer?.name} · {formatDate(a.created_at)}
            </dd>
            {a.approver && (
              <>
                <dt>Approved by</dt>
                <dd>
                  {a.approver.name} · {formatDate(a.approved_at)}
                </dd>
              </>
            )}
            <dt>Budget</dt>
            <dd>
              {formatPeso(a.total_budget)} planned ·{" "}
              {formatPeso(a.requested_amount)} on purchase requests ·{" "}
              {formatPeso(a.utilized_amount)} approved
            </dd>
          </dl>
          <div className="table-wrap" style={{ marginTop: "1rem" }}>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Description</th>
                  <th>Department</th>
                  <th>Mode</th>
                  <th>Schedule</th>
                  <th className="num">Qty</th>
                  <th className="num">Budget</th>
                  <th className="num">Requested</th>
                  <th>Utilized</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {a.items?.map((l) =>
                  edit ? (
                    <EditRow
                      key={l.id}
                      l={l}
                      v={edit[l.id]}
                      options={options}
                      onPatch={(p) => patch(l.id, p)}
                    />
                  ) : (
                    <ViewRow key={l.id} l={l} />
                  ),
                )}
              </tbody>
            </table>
          </div>
          {(edit || a.can_edit || a.can_delete) && (
            <div className="modal-footer">
              {edit ? (
                <>
                  <span className="muted footer-note">
                    Quantities cannot go below what purchase requests already
                    take.
                  </span>
                  <button
                    className="btn btn-secondary"
                    onClick={() => setEdit(null)}
                  >
                    Cancel
                  </button>
                  <button
                    className="btn btn-primary"
                    onClick={() => save.mutate()}
                    disabled={busy}
                  >
                    {save.isPending ? "Saving…" : "Save APP"}
                  </button>
                </>
              ) : (
                <>
                  {a.can_delete && (
                    <button
                      className="btn btn-danger"
                      onClick={() => setDeleting(true)}
                    >
                      Delete
                    </button>
                  )}
                  {a.can_edit && (
                    <button
                      className="btn btn-secondary"
                      onClick={() => startEdit(a)}
                    >
                      Edit lines
                    </button>
                  )}
                  {a.can_approve && (
                    <button
                      className="btn btn-primary"
                      onClick={() => approve.mutate()}
                      disabled={busy}
                    >
                      {approve.isPending ? "Approving…" : "Approve APP"}
                    </button>
                  )}
                  {a.can_edit && !a.can_approve && (
                    <span className="muted footer-note">
                      Someone other than the consolidator approves the APP.
                    </span>
                  )}
                </>
              )}
            </div>
          )}
          {deleting && (
            <ConfirmDialog
              title="Delete APP"
              message={`Delete ${a.app_number}? Its PPMPs can then be consolidated again. The number is not reused.`}
              confirmLabel="Delete APP"
              busy={remove.isPending}
              onConfirm={() => remove.mutate()}
              onCancel={() => setDeleting(false)}
            />
          )}
        </>
      )}
    </Modal>
  );
}

function ViewRow({ l }: { l: AppLine }) {
  return (
    <tr>
      <td>
        {l.general_description}
        <div className="muted small">{l.ppmp_number}</div>
      </td>
      <td>{l.department?.name ?? "—"}</td>
      <td>{l.procurement_mode}</td>
      <td>{l.schedule ? QUARTER_LABEL[l.schedule] : "—"}</td>
      <td className="num">
        {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
      </td>
      <td className="num">{formatPeso(l.estimated_budget)}</td>
      <td className="num">
        {formatQty(l.requested_quantity)}{" "}
        <span className="muted">({formatQty(l.remaining_quantity)} left)</span>
      </td>
      <td>
        <Meter rate={l.utilization_rate} />{" "}
        <span className="small">{l.utilization_rate}%</span>
      </td>
      <td>
        <Badge map={LINE_STATUS} value={l.status} />
      </td>
    </tr>
  );
}

function EditRow({
  l,
  v,
  options,
  onPatch,
}: {
  l: AppLine;
  v: AppLineInput;
  options?: ProcurementOptions;
  onPatch: (p: Partial<AppLineInput>) => void;
}) {
  return (
    <tr>
      <td>
        <input
          aria-label="Description"
          value={v.general_description}
          onChange={(e) => onPatch({ general_description: e.target.value })}
          maxLength={255}
        />
      </td>
      <td>{l.department?.name ?? "—"}</td>
      <td>
        <select
          aria-label="Mode"
          value={v.procurement_mode}
          onChange={(e) => onPatch({ procurement_mode: e.target.value })}
        >
          {options?.modes.map((m) => (
            <option key={m}>{m}</option>
          ))}
        </select>
      </td>
      <td>
        <select
          aria-label="Schedule"
          value={v.schedule ?? ""}
          onChange={(e) => onPatch({ schedule: e.target.value || null })}
        >
          <option value="">—</option>
          {options?.quarters.map((q) => (
            <option key={q}>{q}</option>
          ))}
        </select>
      </td>
      <td className="num">
        <input
          className="qty-input"
          type="number"
          min={Math.max(1, l.requested_quantity)}
          aria-label="Quantity"
          value={v.quantity}
          onChange={(e) => onPatch({ quantity: Number(e.target.value) })}
        />
      </td>
      <td className="num">
        <input
          className="qty-input"
          style={{ width: "8rem" }}
          type="number"
          min={0}
          step="0.01"
          aria-label="Budget"
          value={v.estimated_budget}
          onChange={(e) =>
            onPatch({ estimated_budget: Number(e.target.value) })
          }
        />
      </td>
      <td className="num">{formatQty(l.requested_quantity)}</td>
      <td colSpan={2}></td>
    </tr>
  );
}
