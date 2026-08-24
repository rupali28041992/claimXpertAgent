package com.nextgen.claims.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Generates 5 sample PDFs for end-to-end MEDICAL claim testing.
 *
 * Usage (from claim-assistant-backend/):
 *   mvn compile exec:java "-Dexec.mainClass=com.nextgen.claims.util.SampleDocumentGenerator"
 *
 * Upload all 5 to POST /api/docvalidation/claims with claimType=MEDICAL.
 *
 * Detection keywords (DocumentEvidenceExtractor):
 *   DISCHARGE_SUMMARY  -> "discharge summary" | "discharge date"
 *   HOSPITAL_BILL      -> "hospital bill"     | "bill amount" | "total amount"
 *   PRESCRIPTION       -> "prescription"      | "prescribed"
 *   DIAGNOSTIC_REPORT  -> "diagnostic report" | "lab report"  | "test result"
 *
 * IMPORTANT: hospital_bill and payment_receipt must NOT contain "Discharge Date"
 * or "Discharge Summary" — those words override detection to DISCHARGE_SUMMARY.
 */
public class SampleDocumentGenerator {

    private static final String OUTPUT_DIR = "sample-docs";

    public static void main(String[] args) throws IOException {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        generateDischargeSummary(dir);
        generateHospitalBill(dir);
        generatePaymentReceipt(dir);
        generatePrescription(dir);
        generateDiagnosticReport(dir);

        System.out.println("\nAll 5 sample documents created in: " + dir.getAbsolutePath());
        System.out.println("\nUpload ALL 5 files together with:");
        System.out.println("  claimType  = MEDICAL");
        System.out.println("  claimReason= Hospitalization");
    }

    // -----------------------------------------------------------------------
    // 1. Discharge Summary  -> detected as DISCHARGE_SUMMARY
    // -----------------------------------------------------------------------
    private static void generateDischargeSummary(File dir) throws IOException {
        List<String> lines = List.of(
                "APOLLO CITY HOSPITAL",
                "123, MG Road, Bangalore - 560001 | NABH Accredited",
                "",
                "DISCHARGE SUMMARY",
                "--------------------------------------------",
                "Patient Name  : Rahul Sharma",
                "Age / Gender  : 35 Years / Male",
                "Hospital Name : Apollo City Hospital",
                "Ward          : Surgical Ward - Room 204",
                "Admission Date: 10-01-2024",
                "Discharge Date: 15-01-2024",
                "Policy Number : POL-2024-001234",
                "Claim Number  : CLM-2024-789012",
                "",
                "Diagnosis     : Acute Appendicitis",
                "Treatment     : Laparoscopic Appendectomy performed on 11-01-2024.",
                "               Patient recovered well. No post-operative complications.",
                "",
                "Discharge Advice:",
                "1. Rest for 2 weeks. Avoid strenuous activity.",
                "2. Oral antibiotics for 5 days as prescribed.",
                "3. Follow-up visit in 10 days.",
                "",
                "Treating Physician: Dr. Arjun Kapoor (MS - General Surgery), Reg: MCI-12345"
        );
        writePdf(new File(dir, "discharge_summary_sample.pdf"), lines);
        System.out.println("Created: discharge_summary_sample.pdf  [type=DISCHARGE_SUMMARY]");
    }

    // -----------------------------------------------------------------------
    // 2. Hospital Bill  -> detected as HOSPITAL_BILL
    //    NO "Discharge Date" / "Discharge Summary" keywords — those would
    //    override detection to DISCHARGE_SUMMARY.
    // -----------------------------------------------------------------------
    private static void generateHospitalBill(File dir) throws IOException {
        List<String> lines = List.of(
                "APOLLO CITY HOSPITAL",
                "123, MG Road, Bangalore - 560001",
                "Tel: 080-12345678",
                "",
                "HOSPITAL BILL",
                "--------------------------------------------",
                "Bill No       : BILL-2024-001",
                "Bill Date     : 15-01-2024",
                "",
                "Patient Name  : Rahul Sharma",
                "Hospital Name : Apollo City Hospital",
                "Date of Stay  : 10-01-2024 to 15-01-2024 (5 days)",
                "Diagnosis     : Acute Appendicitis",
                "Treatment     : Appendectomy Surgery",
                "Policy Number : POL-2024-001234",
                "Claim Number  : CLM-2024-789012",
                "",
                "ITEMISED BILL",
                "--------------------------------------------",
                "Room Charges (5 days x Rs.2000)  : Rs.  10,000",
                "Surgery Charges                  : Rs.  25,000",
                "Anaesthesia                      : Rs.   5,000",
                "Medicines & Consumables          : Rs.   3,000",
                "Diagnostic Tests                 : Rs.   2,000",
                "--------------------------------------------",
                "Bill Amount   : Rs. 45,000",
                "Total Amount  : Rs. 45,000",
                "--------------------------------------------",
                "",
                "This is an original itemised hospital bill.",
                "Authorised Signatory: Dr. Priya Mehta (Chief Billing Officer)"
        );
        writePdf(new File(dir, "hospital_bill_sample.pdf"), lines);
        System.out.println("Created: hospital_bill_sample.pdf       [type=HOSPITAL_BILL]");
    }

    // -----------------------------------------------------------------------
    // 3. Payment Receipt  -> detected as HOSPITAL_BILL (via "Total Amount")
    //    NO "Discharge Date" keyword to avoid DISCHARGE_SUMMARY override.
    // -----------------------------------------------------------------------
    private static void generatePaymentReceipt(File dir) throws IOException {
        List<String> lines = List.of(
                "APOLLO CITY HOSPITAL",
                "123, MG Road, Bangalore - 560001",
                "",
                "PAYMENT RECEIPT",
                "--------------------------------------------",
                "Receipt No    : REC-2024-001",
                "Receipt Date  : 15-01-2024",
                "",
                "Patient Name  : Rahul Sharma",
                "Hospital Name : Apollo City Hospital",
                "Period of Care: 10-01-2024 to 15-01-2024",
                "Diagnosis     : Acute Appendicitis",
                "Policy Number : POL-2024-001234",
                "Claim Number  : CLM-2024-789012",
                "",
                "PAYMENT DETAILS",
                "--------------------------------------------",
                "Bill Amount   : Rs. 45,000",
                "Amount Paid   : Rs. 45,000",
                "Payment Mode  : Insurance / Cashless",
                "Payment Date  : 15-01-2024",
                "Total Amount  : Rs. 45,000",
                "Balance Due   : Rs. 0",
                "--------------------------------------------",
                "",
                "Original payment receipt. Thank you for choosing Apollo City Hospital.",
                "Authorised Signatory: Accounts Department"
        );
        writePdf(new File(dir, "payment_receipt_sample.pdf"), lines);
        System.out.println("Created: payment_receipt_sample.pdf     [type=HOSPITAL_BILL]");
    }

    // -----------------------------------------------------------------------
    // 4. Doctor's Prescription  -> detected as PRESCRIPTION
    // -----------------------------------------------------------------------
    private static void generatePrescription(File dir) throws IOException {
        List<String> lines = List.of(
                "DR. ARJUN KAPOOR",
                "MS - General Surgery | Apollo City Hospital",
                "Reg. No: MCI-12345",
                "",
                "PRESCRIPTION",
                "--------------------------------------------",
                "Date          : 15-01-2024",
                "Patient Name  : Rahul Sharma",
                "Age           : 35 Years",
                "Diagnosis     : Post-operative Acute Appendicitis",
                "Policy Number : POL-2024-001234",
                "",
                "PRESCRIBED MEDICINES",
                "--------------------------------------------",
                "1. Amoxicillin 500mg     - 1 tablet 3x daily for 5 days",
                "2. Paracetamol 650mg     - 1 tablet SOS (if pain)",
                "3. Pantoprazole 40mg     - 1 tablet before breakfast for 7 days",
                "4. Wound dressing kit   - Change dressing every 2 days",
                "",
                "PRESCRIBED TESTS",
                "--------------------------------------------",
                "1. Complete Blood Count (CBC) - after 7 days",
                "2. Ultrasound Abdomen         - after 10 days",
                "",
                "Follow-up: 10 days from today.",
                "Doctor's Signature: Dr. Arjun Kapoor"
        );
        writePdf(new File(dir, "prescription_sample.pdf"), lines);
        System.out.println("Created: prescription_sample.pdf        [type=PRESCRIPTION]");
    }

    // -----------------------------------------------------------------------
    // 5. Diagnostic Report  -> detected as DIAGNOSTIC_REPORT
    // -----------------------------------------------------------------------
    private static void generateDiagnosticReport(File dir) throws IOException {
        List<String> lines = List.of(
                "APOLLO DIAGNOSTICS - LABORATORY REPORT",
                "Apollo City Hospital, Bangalore - 560001",
                "",
                "DIAGNOSTIC REPORT",
                "--------------------------------------------",
                "Report No     : LAB-2024-4521",
                "Report Date   : 10-01-2024",
                "Patient Name  : Rahul Sharma",
                "Age / Gender  : 35 / Male",
                "Referred By   : Dr. Arjun Kapoor",
                "Diagnosis     : Suspected Acute Appendicitis",
                "Policy Number : POL-2024-001234",
                "",
                "TEST RESULTS",
                "--------------------------------------------",
                "Test: Complete Blood Count (CBC)",
                "  WBC          : 14,200 cells/uL   [H]  (Normal: 4,500-11,000)",
                "  RBC          : 4.8 million/uL         (Normal: 4.5-5.5)",
                "  Haemoglobin  : 13.5 g/dL              (Normal: 13-17)",
                "  Platelets    : 280,000/uL              (Normal: 150,000-400,000)",
                "",
                "Test: Ultrasound Abdomen",
                "  Finding      : Dilated appendix ~9mm with periappendiceal fat stranding.",
                "  Impression   : Consistent with Acute Appendicitis.",
                "",
                "Test Result   : Positive for Acute Appendicitis",
                "--------------------------------------------",
                "Lab Technician: Mr. Suresh Nair (DMLT)",
                "Pathologist   : Dr. Meena Rao (MD Pathology)"
        );
        writePdf(new File(dir, "diagnostic_report_sample.pdf"), lines);
        System.out.println("Created: diagnostic_report_sample.pdf   [type=DIAGNOSTIC_REPORT]");
    }

    // -----------------------------------------------------------------------
    // PDF writer
    // -----------------------------------------------------------------------
    private static void writePdf(File outputFile, List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(fontRegular, 11);
                cs.setLeading(18f);
                cs.newLineAtOffset(50, 770);

                for (String line : lines) {
                    boolean isHeader = !line.isBlank()
                            && !line.startsWith("-")
                            && !line.startsWith(" ")
                            && line.equals(line.toUpperCase());
                    cs.setFont(isHeader ? fontBold : fontRegular, isHeader ? 12 : 11);
                    cs.showText(line);
                    cs.newLine();
                }

                cs.endText();
            }

            document.save(outputFile);
        }
    }
}
