package com.nextgen.claims;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Run via: mvn exec:java -Dexec.mainClass=com.nextgen.claims.GenerateTravelTestDocs
 * Generates 4 travel claim test PDFs into sample-docs/
 */
public class GenerateTravelTestDocs {

    static final PDType1Font BOLD    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDType1Font MONO    = new PDType1Font(Standard14Fonts.FontName.COURIER);

    public static void main(String[] args) throws IOException {
        File outDir = new File("sample-docs");
        outDir.mkdirs();

        createBoardingPass(outDir);
        createAirlineDelayCertificate(outDir);
        createPolicyBond(outDir);
        createBaggageLossReport(outDir);
        createTripCancellationConfirmation(outDir);

        System.out.println("All 5 travel test documents created in: " + outDir.getAbsolutePath());
    }

    // ── 1. BOARDING PASS ─────────────────────────────────────────────────────

    static void createBoardingPass(File dir) throws IOException {
        List<String[]> lines = List.of(
            new String[]{"bold",    "INDIGO AIRLINES — BOARDING PASS"},
            new String[]{"blank",   ""},
            new String[]{"regular", "Flight       : 6E-512"},
            new String[]{"regular", "Airline      : IndiGo Airlines"},
            new String[]{"regular", "Passenger Name: Rahul Sharma"},
            new String[]{"regular", "From         : DEL  (Indira Gandhi International Airport, New Delhi)"},
            new String[]{"regular", "To           : BOM  (Chhatrapati Shivaji Maharaj International, Mumbai)"},
            new String[]{"regular", "Trip Date    : 15 Aug 2026"},
            new String[]{"regular", "Departure    : 06:45 IST"},
            new String[]{"regular", "Arrival      : 09:10 IST"},
            new String[]{"regular", "Seat         : 14C   Class: Economy"},
            new String[]{"regular", "Itinerary Ref: ABCD1234567"},
            new String[]{"blank",   ""},
            new String[]{"regular", "Policy Number: TRV-2026-00845"},
            new String[]{"regular", "Total Amount : INR 4,850"},
            new String[]{"blank",   ""},
            new String[]{"mono",    "Boarding Pass issued by IndiGo Airlines. This document is"},
            new String[]{"mono",    "required for travel claim processing."}
        );
        writePdf(dir, "travel_boarding_pass.pdf", "Boarding Pass — IndiGo Airlines 6E-512", lines);
        System.out.println("Created: travel_boarding_pass.pdf");
    }

    // ── 2. AIRLINE DELAY CERTIFICATE ─────────────────────────────────────────

    static void createAirlineDelayCertificate(File dir) throws IOException {
        List<String[]> lines = List.of(
            new String[]{"bold",    "AIR INDIA — FLIGHT DELAY CERTIFICATE"},
            new String[]{"blank",   ""},
            new String[]{"regular", "Certificate No : AI-DELAY-2026-3391"},
            new String[]{"regular", "Airline        : Air India"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "FLIGHT DELAY DETAILS"},
            new String[]{"regular", "Flight         : AI-131"},
            new String[]{"regular", "Trip Date      : 20 Aug 2026"},
            new String[]{"regular", "Route          : Mumbai (BOM) -> London Heathrow (LHR)"},
            new String[]{"regular", "Scheduled Dep  : 01:30 IST"},
            new String[]{"regular", "Actual Dep     : 07:55 IST"},
            new String[]{"regular", "Delay Duration : 6 hours 25 minutes"},
            new String[]{"regular", "Reason         : Technical fault — aircraft maintenance required"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "PASSENGER DETAILS"},
            new String[]{"regular", "Passenger Name : Priya Verma"},
            new String[]{"regular", "Booking Ref    : LMPQR7782"},
            new String[]{"regular", "Policy Number  : TRV-2026-01122"},
            new String[]{"blank",   ""},
            new String[]{"regular", "This certificate is issued by Air India to confirm the flight"},
            new String[]{"regular", "delay for insurance claim purposes. The airline accepts"},
            new String[]{"regular", "responsibility for the stated delay duration."},
            new String[]{"blank",   ""},
            new String[]{"regular", "Authorised Signatory: Customer Relations, Air India"},
            new String[]{"regular", "Date of Issue: 20 Aug 2026"}
        );
        writePdf(dir, "travel_airline_delay_certificate.pdf",
                "Airline Delay Certificate — Air India AI-131", lines);
        System.out.println("Created: travel_airline_delay_certificate.pdf");
    }

    // ── 3. TRAVEL INSURANCE POLICY BOND ──────────────────────────────────────

    static void createPolicyBond(File dir) throws IOException {
        List<String[]> lines = List.of(
            new String[]{"bold",    "TRAVEL INSURANCE POLICY BOND"},
            new String[]{"regular", "ClaimXpert General Insurance Co. Ltd."},
            new String[]{"blank",   ""},
            new String[]{"regular", "Policy Number  : TRV-2026-00845"},
            new String[]{"regular", "Insured Name   : Rahul Sharma"},
            new String[]{"regular", "Trip           : New Delhi to Mumbai (Round Trip)"},
            new String[]{"regular", "Itinerary      : DEL-BOM-DEL"},
            new String[]{"regular", "Trip Date      : 15 Aug 2026 to 18 Aug 2026"},
            new String[]{"regular", "Airline        : IndiGo Airlines"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "COVERAGE SUMMARY"},
            new String[]{"regular", "Flight Delay (>4 hrs)   : INR 5,000 per event"},
            new String[]{"regular", "Baggage Loss / Damage   : INR 25,000"},
            new String[]{"regular", "Trip Cancellation       : Up to INR 50,000"},
            new String[]{"regular", "Missed Connection       : INR 10,000"},
            new String[]{"regular", "Medical Emergency Abroad: INR 2,00,000"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "POLICY DETAILS"},
            new String[]{"regular", "Premium Paid   : INR 1,200"},
            new String[]{"regular", "Total Amount   : INR 1,200"},
            new String[]{"regular", "Issue Date     : 10 Aug 2026"},
            new String[]{"regular", "Valid Until    : 19 Aug 2026"},
            new String[]{"blank",   ""},
            new String[]{"regular", "This policy bond is the official travel insurance document."},
            new String[]{"regular", "Please carry this document during your trip."},
            new String[]{"blank",   ""},
            new String[]{"regular", "ClaimXpert General Insurance Co. Ltd."},
            new String[]{"regular", "IRDAI Reg. No: 145  |  CIN: U66010MH2000PLC128503"}
        );
        writePdf(dir, "travel_policy_bond.pdf", "Travel Insurance Policy Bond — TRV-2026-00845", lines);
        System.out.println("Created: travel_policy_bond.pdf");
    }

    // ── 4. BAGGAGE LOSS REPORT (PIR) ─────────────────────────────────────────

    static void createBaggageLossReport(File dir) throws IOException {
        List<String[]> lines = List.of(
            new String[]{"bold",    "PROPERTY IRREGULARITY REPORT (PIR)"},
            new String[]{"regular", "IndiGo Airlines — Baggage Services"},
            new String[]{"blank",   ""},
            new String[]{"regular", "PIR Reference  : 6E-PIR-2026-78834"},
            new String[]{"regular", "Date of Report : 15 Aug 2026"},
            new String[]{"regular", "Airport        : Chhatrapati Shivaji Maharaj International, Mumbai"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "PASSENGER DETAILS"},
            new String[]{"regular", "Passenger Name : Rahul Sharma"},
            new String[]{"regular", "Flight         : 6E-512  (DEL -> BOM)"},
            new String[]{"regular", "Airline        : IndiGo Airlines"},
            new String[]{"regular", "Trip Date      : 15 Aug 2026"},
            new String[]{"regular", "Booking Ref    : ABCD1234567"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "BAGGAGE DETAILS"},
            new String[]{"regular", "Baggage Tag No : 6E123456789"},
            new String[]{"regular", "Number of Bags : 1 (one)"},
            new String[]{"regular", "Weight         : 22 kg"},
            new String[]{"regular", "Description    : Black Samsonite hard-shell trolley bag"},
            new String[]{"regular", "Status         : LOST — not located after 72 hours"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "CLAIM DETAILS"},
            new String[]{"regular", "Declared Value : INR 18,500"},
            new String[]{"regular", "Total Amount   : INR 18,500"},
            new String[]{"regular", "Policy Number  : TRV-2026-00845"},
            new String[]{"regular", "Claim Number   : CLM-BAGGAGE-2026-001"},
            new String[]{"blank",   ""},
            new String[]{"regular", "This Property Irregularity Report is issued by IndiGo Airlines"},
            new String[]{"regular", "Baggage Services as official confirmation that the passenger's"},
            new String[]{"regular", "baggage was lost in transit and not recovered within 72 hours."},
            new String[]{"blank",   ""},
            new String[]{"regular", "Baggage Services Officer: Amit Tiwari"},
            new String[]{"regular", "Stamp: IndiGo Airlines Baggage — BOM Station"}
        );
        writePdf(dir, "travel_baggage_loss_report.pdf",
                "Property Irregularity Report — Baggage Loss (PIR)", lines);
        System.out.println("Created: travel_baggage_loss_report.pdf");
    }

    // ── 5. TRIP CANCELLATION CONFIRMATION ────────────────────────────────────

    static void createTripCancellationConfirmation(File dir) throws IOException {
        List<String[]> lines = List.of(
            new String[]{"bold",    "INDIGO AIRLINES — TRIP CANCELLATION CONFIRMATION"},
            new String[]{"blank",   ""},
            new String[]{"regular", "Cancellation Ref  : 6E-CANCEL-2026-00421"},
            new String[]{"regular", "Date of Issue     : 10 Sep 2026"},
            new String[]{"regular", "Airline           : IndiGo Airlines"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "FLIGHT DETAILS"},
            new String[]{"regular", "Flight            : 6E-839"},
            new String[]{"regular", "Route             : New Delhi (DEL) -> Bengaluru (BLR)"},
            new String[]{"regular", "Trip Date         : 12 Sep 2026"},
            new String[]{"regular", "Scheduled Dep     : 09:00 IST"},
            new String[]{"regular", "Class             : Economy"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "PASSENGER DETAILS"},
            new String[]{"regular", "Passenger Name    : Anjali Mehta"},
            new String[]{"regular", "Booking Ref       : PQRS5566778"},
            new String[]{"regular", "Policy Number     : TRV-2026-01987"},
            new String[]{"blank",   ""},
            new String[]{"bold",    "CANCELLATION DETAILS"},
            new String[]{"regular", "Cancellation Type : Trip Cancellation before departure"},
            new String[]{"regular", "Cancellation Date : 09 Sep 2026"},
            new String[]{"regular", "Reason            : Medical emergency — passenger hospitalised"},
            new String[]{"regular", "Refund Status     : Partial refund processed (INR 2,400)"},
            new String[]{"regular", "Non-Refundable    : INR 3,850 (booking fees + convenience fee)"},
            new String[]{"regular", "Total Amount      : INR 6,250"},
            new String[]{"regular", "Claim Number      : CLM-CANCEL-2026-001"},
            new String[]{"blank",   ""},
            new String[]{"regular", "This document confirms that the above-named passenger cancelled"},
            new String[]{"regular", "their trip before departure. The stated reason has been recorded"},
            new String[]{"regular", "and the partial refund has been processed as per airline policy."},
            new String[]{"blank",   ""},
            new String[]{"regular", "This cancellation confirmation is issued for travel insurance"},
            new String[]{"regular", "claim purposes. Original booking invoice is attached."},
            new String[]{"blank",   ""},
            new String[]{"regular", "Customer Relations: IndiGo Airlines"},
            new String[]{"regular", "Authorised Signatory: Preethi Nair, Claims Team"}
        );
        writePdf(dir, "travel_trip_cancellation_confirmation.pdf",
                "Trip Cancellation Confirmation — IndiGo Airlines 6E-839", lines);
        System.out.println("Created: travel_trip_cancellation_confirmation.pdf");
    }

    // ── PDF WRITER ────────────────────────────────────────────────────────────

    static void writePdf(File dir, String filename, String title, List<String[]> lines)
            throws IOException {

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin   = 60;
                float y        = page.getMediaBox().getHeight() - margin;
                float leading  = 18;
                float titleSize = 14;
                float bodySize  = 11;

                // Header bar
                cs.setNonStrokingColor(0.18f, 0.35f, 0.55f);
                cs.addRect(margin - 10, y - 5, page.getMediaBox().getWidth() - 2 * margin + 20, titleSize + 12);
                cs.fill();

                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.beginText();
                cs.setFont(BOLD, titleSize);
                cs.newLineAtOffset(margin, y);
                cs.showText(title);
                cs.endText();

                y -= (leading * 2.2f);
                cs.setNonStrokingColor(0f, 0f, 0f);

                for (String[] entry : lines) {
                    String type = entry[0];
                    String text = entry.length > 1 ? entry[1] : "";

                    if ("blank".equals(type)) {
                        y -= leading * 0.6f;
                        continue;
                    }

                    if (y < margin + leading) {
                        // New page
                        cs.endText();
                        page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        y = page.getMediaBox().getHeight() - margin;
                    }

                    PDType1Font font = switch (type) {
                        case "bold"    -> BOLD;
                        case "mono"    -> MONO;
                        default        -> REGULAR;
                    };
                    float size = "bold".equals(type) ? bodySize + 0.5f : bodySize;

                    if ("bold".equals(type)) {
                        cs.setNonStrokingColor(0.18f, 0.35f, 0.55f);
                    } else {
                        cs.setNonStrokingColor(0f, 0f, 0f);
                    }

                    cs.beginText();
                    cs.setFont(font, size);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(text);
                    cs.endText();

                    y -= leading;
                }
            }

            doc.save(new File(dir, filename));
        }
    }
}
