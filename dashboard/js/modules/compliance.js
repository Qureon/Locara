/**
 * ==============================================================================
 * ZERO-HEALING DASHBOARD — UAT COMPLIANCE & SIGN-OFF MODULE
 * Formal audit certificate verification and printable document generator.
 * ==============================================================================
 */

export function initComplianceModule() {
    const btnPrint = document.getElementById("btn-print-certificate");
    if (btnPrint) {
        btnPrint.addEventListener("click", () => {
            window.print();
        });
    }

    const btnExportReport = document.getElementById("btn-export-html-report");
    if (btnExportReport) {
        btnExportReport.addEventListener("click", () => {
            window.open("/api/report", "_blank");
        });
    }
}
