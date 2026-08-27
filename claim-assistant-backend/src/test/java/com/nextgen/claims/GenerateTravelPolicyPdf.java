package com.nextgen.claims;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.io.*;
import java.util.*;

/**
 * Run: mvn test-compile exec:java -Dexec.mainClass=com.nextgen.claims.GenerateTravelPolicyPdf -Dexec.classpathScope=test
 * Generates: RAG_IngestionPolicyPdfs/Travel_Insurance_Policy_ClaimXpert.pdf
 */
public class GenerateTravelPolicyPdf {

    // ── Brand colours ──────────────────────────────────────────────────────
    static final float[] DARK_BLUE  = {0.086f, 0.239f, 0.322f};   // #163D52
    static final float[] ORANGE     = {0.878f, 0.482f, 0.220f};   // #E07B38
    static final float[] LIGHT_BLUE = {0.933f, 0.969f, 0.988f};   // #EEF7FC
    static final float[] MID_BLUE   = {0.710f, 0.855f, 0.925f};   // #B5DAEC
    static final float[] GOLD_BG    = {0.996f, 0.980f, 0.925f};   // #FEF9EC
    static final float[] GOLD_BORDER= {0.871f, 0.808f, 0.502f};   // #DECF80
    static final float[] TEXT_DARK  = {0.102f, 0.176f, 0.235f};   // #1A2D3C
    static final float[] TEXT_MED   = {0.251f, 0.310f, 0.380f};   // #404F61
    static final float[] GREY_LINE  = {0.820f, 0.859f, 0.886f};   // #D1DBE2
    static final float[] WHITE      = {1f, 1f, 1f};

    static final PDType1Font BOLD    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDType1Font OBLIQUE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    static final float PW = PDRectangle.A4.getWidth();
    static final float PH = PDRectangle.A4.getHeight();
    static final float ML = 54f;
    static final float MR = PW - 54f;
    static final float CONTENT_W = MR - ML;

    // Stateful layout cursor
    PDDocument doc;
    PDPage page;
    PDPageContentStream cs;
    float y;

    public static void main(String[] args) throws IOException {
        File outDir = new File("RAG_IngestionPolicyPdfs");
        outDir.mkdirs();
        File out = new File(outDir, "Travel_Insurance_Policy_ClaimXpert.pdf");
        new GenerateTravelPolicyPdf().generate(out);
        System.out.println("Created: " + out.getAbsolutePath());
    }

    void generate(File out) throws IOException {
        doc = new PDDocument();
        addCoverPage();
        addTocPage();
        addDefinitionsPage();
        addPartI();
        addPartII();
        addPartIII();
        addPartIV();
        addPartV();
        addPartVI();
        addPartVII();
        addPartVIII();
        addPartIX();
        if (cs != null) cs.close();
        doc.save(out);
        doc.close();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COVER PAGE
    // ══════════════════════════════════════════════════════════════════════
    void addCoverPage() throws IOException {
        newPage();

        // Brand name — "Claim" dark blue, "Xpert" orange
        float bx = PW / 2 - 60;
        float by = PH - 140;
        drawTextAt(BOLD, 38, DARK_BLUE, "Claim", bx, by);
        float xw = textWidth(BOLD, 38, "Claim");
        drawTextAt(BOLD, 38, ORANGE, "Xpert", bx + xw, by);

        // Tagline
        String tagline = "AI-POWERED INSURANCE CLAIMS PLATFORM";
        float tw = textWidth(BOLD, 9, tagline);
        drawTextAt(BOLD, 9, DARK_BLUE, tagline, (PW - tw) / 2, by - 24);

        // Orange divider
        fillRect(ORANGE, (PW - 60) / 2, by - 44, 60, 3);

        // "POLICY DOCUMENT"
        String pd = "POLICY DOCUMENT";
        float pdw = textWidth(BOLD, 24, pd);
        drawTextAt(BOLD, 24, DARK_BLUE, pd, (PW - pdw) / 2, by - 100);

        // Subtitle
        String sub = "Terms, Conditions & Benefits Schedule";
        float subw = textWidth(REGULAR, 13, sub);
        drawTextAt(REGULAR, 13, TEXT_MED, sub, (PW - subw) / 2, by - 126);

        // Pill box
        float pillW = 230;
        float pillX = (PW - pillW) / 2;
        float pillY = by - 182;
        fillRect(DARK_BLUE, pillX, pillY, pillW, 32);
        String pill = "TRAVEL INSURANCE POLICY";
        float pw2 = textWidth(BOLD, 11, pill);
        drawTextAt(BOLD, 11, WHITE, pill, (PW - pw2) / 2, pillY + 11);

        // Body text
        String body1 = "This document sets out the terms and conditions of the Travel Insurance Policy";
        String body2 = "issued by ClaimXpert Insurance Services. Please read this document carefully and";
        String body3 = "retain it in a safe place for future reference.";
        float b1w = textWidth(REGULAR, 10, body1);
        float b2w = textWidth(REGULAR, 10, body2);
        float b3w = textWidth(REGULAR, 10, body3);
        drawTextAt(REGULAR, 10, TEXT_MED, body1, (PW - b1w) / 2, pillY - 60);
        drawTextAt(REGULAR, 10, TEXT_MED, body2, (PW - b2w) / 2, pillY - 76);
        drawTextAt(REGULAR, 10, TEXT_MED, body3, (PW - b3w) / 2, pillY - 92);

        // Bottom rule
        fillRect(GREY_LINE, ML, 72, CONTENT_W, 0.7f);

        // Footer
        String footer = "ClaimXpert Insurance Services  ·  Regulated by IRDAI  ·  CIN: U66010MH2024PLC123456";
        float fw = textWidth(REGULAR, 8, footer);
        drawTextAt(REGULAR, 8, TEXT_MED, footer, (PW - fw) / 2, 58);

        String meta = "Document Reference: CX-TRV-POL-2024    Version: 1.0    Effective: 01 January 2024    Prepared: August 2026";
        drawTextAt(REGULAR, 8, TEXT_MED, meta, ML, 40);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TABLE OF CONTENTS
    // ══════════════════════════════════════════════════════════════════════
    void addTocPage() throws IOException {
        newPage(); y = PH - 60;
        sectionHeading("TABLE OF CONTENTS");
        y -= 24;

        tocPart("Part I  — Coverage & Eligible Events");
        tocEntry("Section 1.1 - Scope of Cover", 3);
        tocEntry("Section 1.2 - Covered Events", 3);
        tocPart("Part II  — Flight Delay Benefit");
        tocEntry("Section 2.1 - Flight Delay (Domestic)", 4);
        tocEntry("Section 2.2 - Flight Delay (International)", 4);
        tocPart("Part III — Baggage Loss & Damage");
        tocEntry("Section 3.1 - Lost Baggage", 5);
        tocEntry("Section 3.2 - Damaged Baggage", 5);
        tocPart("Part IV  — Trip Cancellation & Curtailment");
        tocEntry("Section 4.1 - Trip Cancellation", 6);
        tocEntry("Section 4.2 - Trip Curtailment", 6);
        tocPart("Part V   — Missed Connection");
        tocEntry("Section 5.1 - Missed Connection Benefit", 7);
        tocPart("Part VI  — Medical Emergency Abroad");
        tocEntry("Section 6.1 - Emergency Medical Expenses", 7);
        tocEntry("Section 6.2 - Medical Evacuation", 8);
        tocPart("Part VII — Exclusions");
        tocEntry("Section 7.1 - Permanent Exclusions", 8);
        tocEntry("Section 7.2 - War & Terrorism Exclusions", 9);
        tocPart("Part VIII — Claims Process");
        tocEntry("Section 8.1 - How to Lodge a Claim", 9);
        tocEntry("Section 8.2 - Required Claim Documents", 9);
        tocPart("Part IX  — Sum Insured & Limits");
        tocEntry("Section 9.1 - Benefit Limits Schedule", 10);
        tocEntry("Section 9.2 - No Claim Bonus", 10);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DEFINITIONS
    // ══════════════════════════════════════════════════════════════════════
    void addDefinitionsPage() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Key Definitions", "Terms used throughout this travel insurance policy document");
        y -= 18;
        definitionTable(new String[][]{
            {"Insured",            "The person(s) named in the policy schedule who are covered under this policy."},
            {"Policyholder",       "The individual or entity in whose name the policy is issued and who is responsible for premium payment."},
            {"Sum Insured",        "The maximum amount payable by the insurer for all claims during a single policy period, as stated in the schedule."},
            {"Trip",               "A pre-planned journey from the insured's home country to one or more destinations and back, for which a return ticket has been purchased."},
            {"Flight Delay",       "A situation where the scheduled departure of a booked flight is delayed by more than the threshold specified in the schedule."},
            {"Baggage",            "Personal luggage and belongings belonging to the insured, checked in or carried onto the aircraft."},
            {"Missed Connection",  "Failure to board a connecting flight due to the late arrival of the incoming flight, both booked on the same itinerary."},
            {"Trip Cancellation",  "The cancellation of a trip prior to departure due to a covered event occurring after the policy purchase date."},
            {"Airline",            "A licensed commercial carrier operating the flight on which the insured holds a confirmed booking."},
            {"Boarding Pass",      "The official document issued by an airline confirming the insured's seat on a specific flight."},
            {"Policy Period",      "The period of insurance as stated in the policy schedule, commencing on the departure date and ending on the return date."},
        });
        y -= 16;
        noticeBox("Important Notice to Policyholders",
            "This policy document should be read in conjunction with the Policy Schedule, which sets out individual\n" +
            "benefit limits, deductibles, and applicable excesses for your specific plan. In the event of any conflict\n" +
            "between this document and the Schedule, the Schedule prevails.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART I — Coverage
    // ══════════════════════════════════════════════════════════════════════
    void addPartI() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part I — Coverage & Eligible Events", "Scope of benefits provided under this travel insurance policy");
        section("1.1", "Scope of Cover",
            "This policy covers financial losses and additional expenses incurred by the insured as a direct result of a covered travel disruption event occurring during the policy period. Cover includes flight delay compensation, baggage loss or damage, trip cancellation, missed connection, and emergency medical expenses abroad, subject to the limits, conditions, and exclusions set out in this document and the Policy Schedule.");
        section("1.2", "Covered Events",
            "The following events are covered under this policy: (a) Flight Delay — where a booked flight is delayed for more than 4 hours from the scheduled departure time due to a cause beyond the insured's control; (b) Baggage Loss — where checked baggage is permanently lost by the airline; (c) Baggage Damage — where checked baggage or its contents are damaged in transit; (d) Trip Cancellation — where a trip is cancelled before departure due to a covered reason; (e) Trip Curtailment — where a trip is cut short due to a covered reason; (f) Missed Connection — where the insured misses a connecting flight due to a delayed incoming flight; (g) Medical Emergency Abroad — where the insured requires emergency medical treatment outside their home country.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART II — Flight Delay
    // ══════════════════════════════════════════════════════════════════════
    void addPartII() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part II — Flight Delay Benefit", "Compensation for delayed flights under this travel insurance policy");
        section("2.1", "Flight Delay — Domestic",
            "Flight Delay benefit is payable when the insured's scheduled domestic flight is delayed by more than 4 consecutive hours from the published departure time for reasons beyond the insured's control, including but not limited to: technical or mechanical failure of the aircraft; adverse weather conditions grounding the flight; air traffic control restrictions; or airline operational disruption. The benefit is paid as a fixed daily allowance per the schedule for each completed 4-hour delay period, up to the maximum limit stated in the Policy Schedule. The insured must obtain an official Airline Delay Certificate confirming the delay duration and reason. A boarding pass for the affected flight is also required. Voluntary changes, missed check-in, and passport or visa issues are not covered under this section.");
        section("2.2", "Flight Delay — International",
            "International flight delay benefit applies when a booked international flight is delayed by more than 4 hours from the scheduled departure time due to a covered cause. The benefit amount per hour of delay beyond the 4-hour threshold is as set out in the Policy Schedule. In addition to the fixed delay allowance, the insurer will reimburse reasonable and necessary out-of-pocket expenses incurred during the delay, including meals, refreshments, and accommodation where the airline does not provide these, up to the sub-limit specified in the schedule. Original receipts must be provided. An official Airline Delay Certificate from the carrier is mandatory for all flight delay claims. Claims without an Airline Delay Certificate will not be processed.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART III — Baggage
    // ══════════════════════════════════════════════════════════════════════
    void addPartIII() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part III — Baggage Loss & Damage", "Cover for lost, stolen, or damaged baggage during travel");
        section("3.1", "Lost Baggage",
            "Lost Baggage benefit is payable when the insured's checked baggage is permanently lost by the airline and not recovered within 48 hours of arrival at the destination. The insured must obtain a Property Irregularity Report (PIR) or Baggage Loss Report from the airline at the time of the incident and prior to leaving the baggage claim area. The insurer will reimburse the replacement value of lost items up to the per-bag limit stated in the schedule, subject to depreciation based on the age of the item. Original purchase receipts, the airline's Property Irregularity Report (PIR), the boarding pass, and a written confirmation from the airline that the baggage was not recovered are required. Baggage delayed for less than 48 hours is not payable under this section but may qualify under the baggage delay benefit if specified in the schedule.");
        section("3.2", "Damaged Baggage",
            "Damaged Baggage benefit is payable when the insured's checked baggage or its contents are physically damaged during transit in the care of the airline. The insured must report the damage to the airline before leaving the baggage claim area and obtain a Property Irregularity Report (PIR) documenting the damage. The insurer will pay the repair cost or, where repair is uneconomical, the replacement value less depreciation, up to the sub-limit in the schedule. Documentation required: airline Property Irregularity Report, photographs of the damaged baggage, repair estimate or replacement invoice, boarding pass, and the original baggage tag.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART IV — Trip Cancellation
    // ══════════════════════════════════════════════════════════════════════
    void addPartIV() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part IV — Trip Cancellation & Curtailment", "Cover for cancelled or curtailed trips due to covered events");
        section("4.1", "Trip Cancellation",
            "Trip Cancellation benefit is payable for non-refundable travel and accommodation expenses when a trip is cancelled before departure due to any of the following covered reasons: (a) sudden serious illness or accidental injury of the insured, an immediate family member, or a travelling companion, certified by a registered medical practitioner; (b) death of the insured, an immediate family member, or a travelling companion; (c) natural disaster, civil unrest, or government-imposed travel restriction at the destination; (d) redundancy of the insured (subject to eligibility conditions); (e) jury service or witness summons that cannot be postponed. Cancellation due to change of mind, pre-existing medical conditions (unless specifically covered), or events known at the time of booking are excluded. A boarding pass or confirmed flight itinerary, airline cancellation confirmation, and policy bond are required to support the claim.");
        section("4.2", "Trip Curtailment",
            "Trip Curtailment benefit is payable for unused and non-refundable portions of the trip when the insured is compelled to return home early due to a covered event that arises after departure. Covered reasons mirror those for trip cancellation. The benefit is calculated on a pro-rata basis for the unused days of the trip. The insured must obtain written confirmation of the early return from a treating physician or relevant authority. Required documents: boarding pass for the original and return flights, travel itinerary, medical certificate if applicable, and the original policy bond.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART V — Missed Connection
    // ══════════════════════════════════════════════════════════════════════
    void addPartV() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part V — Missed Connection", "Benefit for missed connecting flights on the same itinerary");
        section("5.1", "Missed Connection Benefit",
            "Missed Connection benefit is payable when the insured misses a connecting flight that is part of the same booked itinerary due to the late arrival of the incoming flight, provided: (a) both the incoming and connecting flights are on the same booking reference or itinerary; (b) the insured had sufficient connection time as per the airline's minimum connection time at the connecting airport; and (c) the delay of the incoming flight was caused by a reason beyond the insured's control. The insurer will reimburse reasonable additional transportation and accommodation costs incurred to reach the final destination, up to the limit in the schedule. An official confirmation from the airline, boarding pass for the missed connection, and the original itinerary are required. Missed connections caused by the insured's own late arrival at the airport, immigration delays, or insufficient connection time booked by the insured are excluded.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART VI — Medical Emergency Abroad
    // ══════════════════════════════════════════════════════════════════════
    void addPartVI() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part VI — Medical Emergency Abroad", "Emergency medical treatment expenses incurred outside the home country");
        section("6.1", "Emergency Medical Expenses",
            "Emergency Medical Expenses abroad are covered when the insured requires urgent and unplanned medical treatment for an illness first manifesting, or an accidental injury occurring, during the trip. The insurer will reimburse reasonable and customary medical expenses including: emergency hospital admission and treatment; surgeon, physician, and specialist fees; prescribed medicines and consumables; diagnostic tests directly related to the emergency condition; and ambulance charges. Expenses must be incurred outside the insured's home country. Treatment must be certified as an emergency by the treating physician. The insurer must be notified as soon as practicable and, wherever possible, prior to any non-emergency admission. Pre-existing conditions are excluded unless the policy schedule specifically endorses cover for a declared pre-existing condition.");
        section("6.2", "Medical Evacuation",
            "Where the insured requires emergency medical evacuation to the nearest appropriate medical facility or repatriation to their home country due to the severity of the medical condition, and such evacuation is certified as medically necessary by the attending physician, the insurer will arrange and bear the cost of evacuation. All evacuation arrangements must be pre-approved by the insurer's 24-hour emergency assistance centre unless circumstances make it impossible to obtain prior approval. Repatriation of mortal remains in the event of the insured's death during the trip is also covered up to the limit in the schedule.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART VII — Exclusions
    // ══════════════════════════════════════════════════════════════════════
    void addPartVII() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part VII — Exclusions", "Events and circumstances not covered under this travel insurance policy");
        section("7.1", "Permanent Exclusions",
            "The following are permanently excluded regardless of policy tenure:");
        exclusionList(new String[]{
            "Flight delays caused by the insured's own late arrival at the airport or failure to check in on time",
            "Baggage losses or damage not reported to the airline before leaving the airport baggage claim area",
            "Trip cancellations due to change of mind, disinclination to travel, or financial circumstances",
            "Claims arising from pre-existing medical conditions not declared and endorsed on the policy",
            "Travel undertaken against the advice of a registered medical practitioner",
            "Travel to destinations under a government-issued 'Do Not Travel' advisory at time of booking",
            "Claims arising from participation in extreme sports, professional sports, or hazardous activities unless specifically endorsed",
            "Loss of cash, credit cards, travel documents, or unattended baggage",
            "Wilful misconduct, self-inflicted injury, or travel disruption caused intentionally by the insured",
            "Losses recoverable from an airline, travel operator, or other party under consumer protection legislation",
            "Consequential losses or indirect costs not forming part of the pre-paid trip cost",
        });
        section("7.2", "War & Terrorism Exclusions",
            "No benefit is payable for any travel disruption, bodily injury, or financial loss arising directly or indirectly from: war, invasion, act of foreign enemy, hostilities, civil war, rebellion, revolution, insurrection, military coup, or seizure of power; or any act of terrorism as defined by applicable legislation. This exclusion applies regardless of whether war or terrorism has been formally declared. Travel to a destination that was under a government travel ban or 'Do Not Travel' advisory at the time the trip was booked is also excluded.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART VIII — Claims Process
    // ══════════════════════════════════════════════════════════════════════
    void addPartVIII() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part VIII — Claims Process", "How to lodge a travel insurance claim");
        section("8.1", "How to Lodge a Claim",
            "All claims must be notified to the insurer within 30 days of the event giving rise to the claim. To lodge a claim: (1) Complete the travel claim form available on the ClaimXpert portal; (2) Attach all required supporting documents as listed in Section 8.2; (3) Submit the completed claim through the ClaimXpert online portal or by email to claims@claimxpert.in. The insurer will acknowledge receipt within 2 business days and will aim to process the claim within 15 business days of receiving all required documents. Claims submitted more than 90 days after the incident will not be entertained without a written explanation of the delay approved by the insurer.");
        section("8.2", "Required Claim Documents",
            "The following documents are required for each claim type:");
        numberedList(new String[]{
            "Completed and signed travel claim form",
            "Original boarding pass(es) for all affected flights",
            "For Flight Delay: Official Airline Delay Certificate from the carrier confirming the delay duration and reason",
            "For Baggage Loss / Damage: Property Irregularity Report (Baggage Loss Report) from the airline; photographs of damaged items; repair/replacement invoices",
            "For Trip Cancellation / Curtailment: Airline or travel operator's cancellation confirmation; medical certificate if cancellation is health-related; original booking invoices",
            "For Missed Connection: Written confirmation from the airline of the delay of the incoming flight; original itinerary showing both flights",
            "For Medical Emergency Abroad: Original hospital bills, discharge summary, and all prescription receipts; treating physician's report confirming emergency nature of treatment",
            "Original policy bond / travel insurance policy document",
            "Copy of passport showing travel dates (entry and exit stamps)",
            "Bank account details for reimbursement transfer",
        });
        noticeBox("Claim Submission Tip",
            "Keep all original documents — boarding passes, airline certificates, receipts, and reports.\n" +
            "Photographs of damaged items taken at the airport are strongly recommended.\n" +
            "Notify the airline of any issue before leaving the airport — airline reports obtained later may not be accepted.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PART IX — Limits & NCB
    // ══════════════════════════════════════════════════════════════════════
    void addPartIX() throws IOException {
        newPage(); y = PH - 60;
        partHeader("Part IX — Sum Insured & Benefit Limits", "Applicable benefit limits and no claim bonus provisions");
        section("9.1", "Benefit Limits Schedule",
            "The following indicative benefit limits apply under the standard ClaimXpert Travel Insurance Plan. Actual limits applicable to the insured are stated in the individual Policy Schedule.");
        benefitTable();
        y -= 18;
        section("9.2", "No Claim Bonus",
            "For every claim-free policy year, a No Claim Bonus of 5% of the base sum insured shall be added to the benefit limits for the following policy year, up to a maximum cumulative bonus of 25%. The No Claim Bonus is reduced by 10 percentage points for each year in which a claim is made. The base sum insured is never reduced below the amounts stated in the original Policy Schedule.");
        y -= 14;
        noticeBox("Grievance Redressal",
            "In case of any grievance relating to your policy or claim, please contact our Customer Care at\n" +
            "1800-XXX-XXXX (toll-free) or email grievance@claimxpert.in. If not resolved within 15 days,\n" +
            "you may approach the Insurance Ombudsman in your jurisdiction as per IRDAI guidelines.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REUSABLE LAYOUT HELPERS
    // ══════════════════════════════════════════════════════════════════════

    void newPage() throws IOException {
        if (cs != null) cs.close();
        page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        cs = new PDPageContentStream(doc, page);
        y = PH - 54;
    }

    void ensureSpace(float needed) throws IOException {
        if (y - needed < 60) newPage();
    }

    /** Dark-blue full-width part header with subtitle stripe */
    void partHeader(String title, String subtitle) throws IOException {
        ensureSpace(56);
        fillRect(DARK_BLUE, ML - 4, y - 36, CONTENT_W + 8, 42);
        drawTextAt(BOLD, 13, WHITE, title, ML + 4, y - 8);
        drawTextAt(REGULAR, 8.5f, MID_BLUE, subtitle, ML + 4, y - 24);
        y -= 54;
    }

    /** TOC section heading without the blue box */
    void sectionHeading(String title) throws IOException {
        drawTextAt(BOLD, 14, DARK_BLUE, title, ML, y);
        fillRect(DARK_BLUE, ML, y - 6, CONTENT_W, 1.2f);
        y -= 20;
    }

    /** Section card: orange label, blue title, body text */
    void section(String num, String title, String body) throws IOException {
        float lineH = 15f;
        // Estimate needed height
        List<String> wrapped = wrap(body, REGULAR, 10, CONTENT_W - 24);
        float needed = 14 + 16 + 16 + wrapped.size() * lineH + 20;
        ensureSpace(needed);

        float cardY = y;
        // Light box
        fillRect(LIGHT_BLUE, ML, cardY - needed + 18, CONTENT_W, needed - 18);
        fillRect(ORANGE, ML, cardY - needed + 18, 3, needed - 18);

        // Section label
        drawTextAt(BOLD, 8, ORANGE, "SECTION " + num, ML + 10, cardY - 4);
        y = cardY - 18;

        // Title
        drawTextAt(BOLD, 12, DARK_BLUE, title, ML + 10, y);
        y -= 16;

        // Body
        for (String line : wrapped) {
            drawTextAt(REGULAR, 10, TEXT_DARK, line, ML + 10, y);
            y -= lineH;
        }
        y -= 18;
    }

    /** TOC part heading */
    void tocPart(String label) throws IOException {
        ensureSpace(22);
        drawTextAt(BOLD, 10.5f, DARK_BLUE, label, ML, y);
        fillRect(GREY_LINE, ML, y - 5, CONTENT_W, 0.5f);
        y -= 18;
    }

    /** TOC entry line */
    void tocEntry(String label, int page) throws IOException {
        ensureSpace(16);
        drawTextAt(REGULAR, 9.5f, TEXT_MED, label, ML + 16, y);
        String pg = String.valueOf(page);
        float pgw = textWidth(REGULAR, 9.5f, pg);
        drawTextAt(REGULAR, 9.5f, TEXT_MED, pg, MR - pgw, y);
        fillRect(GREY_LINE, ML + 16 + textWidth(REGULAR, 9.5f, label) + 4, y + 1,
                MR - pgw - ML - 16 - textWidth(REGULAR, 9.5f, label) - 12, 0.5f);
        y -= 15;
    }

    /** Definition table rows */
    void definitionTable(String[][] rows) throws IOException {
        float colW = 130f;
        boolean alt = false;
        for (String[] row : rows) {
            List<String> valLines = wrap(row[1], REGULAR, 9.5f, CONTENT_W - colW - 16);
            float rowH = Math.max(20, valLines.size() * 14 + 8);
            ensureSpace(rowH + 4);
            if (alt) fillRect(LIGHT_BLUE, ML, y - rowH + 4, CONTENT_W, rowH);
            drawTextAt(BOLD, 9.5f, ORANGE, row[0], ML + 6, y - 4);
            for (int i = 0; i < valLines.size(); i++) {
                drawTextAt(REGULAR, 9.5f, TEXT_DARK, valLines.get(i), ML + colW, y - 4 - i * 14);
            }
            fillRect(GREY_LINE, ML, y - rowH + 4, CONTENT_W, 0.4f);
            y -= rowH;
            alt = !alt;
        }
    }

    /** Notice box (golden background) */
    void noticeBox(String heading, String body) throws IOException {
        String[] bodyLines = body.split("\n");
        float needed = 14 + bodyLines.length * 13 + 22;
        ensureSpace(needed);
        fillRect(GOLD_BG, ML, y - needed + 10, CONTENT_W, needed);
        fillRect(GOLD_BORDER, ML, y - needed + 10, 3, needed);
        drawTextAt(BOLD, 9, ORANGE, heading, ML + 10, y - 4);
        float by2 = y - 18;
        for (String bl : bodyLines) {
            drawTextAt(REGULAR, 9, TEXT_MED, bl, ML + 10, by2);
            by2 -= 13;
        }
        y -= needed + 8;
    }

    /** Exclusion bullet list */
    void exclusionList(String[] items) throws IOException {
        for (String item : items) {
            List<String> lines = wrap(item, REGULAR, 9.5f, CONTENT_W - 28);
            ensureSpace(lines.size() * 14 + 6);
            drawTextAt(BOLD, 10, new float[]{0.78f, 0.18f, 0.18f}, "X", ML + 8, y);
            for (int i = 0; i < lines.size(); i++) {
                drawTextAt(REGULAR, 9.5f, TEXT_DARK, lines.get(i), ML + 26, y - i * 14);
            }
            fillRect(GREY_LINE, ML + 8, y - lines.size() * 14 - 2, CONTENT_W - 8, 0.4f);
            y -= lines.size() * 14 + 6;
        }
        y -= 6;
    }

    /** Numbered list */
    void numberedList(String[] items) throws IOException {
        for (int n = 0; n < items.length; n++) {
            List<String> lines = wrap(items[n], REGULAR, 9.5f, CONTENT_W - 30);
            ensureSpace(lines.size() * 14 + 6);
            drawTextAt(BOLD, 9.5f, DARK_BLUE, (n + 1) + ".", ML + 10, y);
            for (int i = 0; i < lines.size(); i++) {
                drawTextAt(REGULAR, 9.5f, TEXT_DARK, lines.get(i), ML + 28, y - i * 14);
            }
            y -= lines.size() * 14 + 6;
        }
        y -= 6;
    }

    /** Benefit limits table */
    void benefitTable() throws IOException {
        String[][] rows = {
            {"Flight Delay (Domestic, >4 hrs)",     "INR 5,000 per event"},
            {"Flight Delay (International, >4 hrs)", "INR 10,000 per event + meal/hotel up to INR 3,000"},
            {"Lost Baggage",                         "INR 25,000 per bag (max 2 bags)"},
            {"Damaged Baggage",                      "INR 15,000 per bag (repair or replacement)"},
            {"Trip Cancellation",                    "Up to INR 50,000 (non-refundable costs)"},
            {"Trip Curtailment",                     "Up to INR 30,000 (unused portion, pro-rata)"},
            {"Missed Connection",                    "INR 10,000 per event (additional transport/hotel)"},
            {"Medical Emergency Abroad",             "Up to INR 2,00,000 per trip"},
            {"Medical Evacuation",                   "Up to INR 5,00,000 (prior approval required)"},
        };
        float col1 = CONTENT_W * 0.58f;
        float col2 = CONTENT_W * 0.42f;
        ensureSpace(20);
        // Header row
        fillRect(DARK_BLUE, ML, y - 18, CONTENT_W, 22);
        drawTextAt(BOLD, 9, WHITE, "Benefit", ML + 8, y - 4);
        drawTextAt(BOLD, 9, WHITE, "Maximum Limit", ML + col1 + 8, y - 4);
        y -= 20;
        boolean alt = false;
        for (String[] row : rows) {
            ensureSpace(22);
            if (alt) fillRect(LIGHT_BLUE, ML, y - 16, CONTENT_W, 20);
            drawTextAt(REGULAR, 9, TEXT_DARK, row[0], ML + 8, y - 4);
            drawTextAt(BOLD, 9, DARK_BLUE, row[1], ML + col1 + 8, y - 4);
            fillRect(GREY_LINE, ML, y - 16, CONTENT_W, 0.4f);
            y -= 20;
            alt = !alt;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOW-LEVEL DRAWING PRIMITIVES
    // ══════════════════════════════════════════════════════════════════════

    void fillRect(float[] rgb, float x, float rectY, float w, float h) throws IOException {
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.addRect(x, rectY, w, h);
        cs.fill();
    }

    void drawTextAt(PDType1Font font, float size, float[] rgb, String text, float x, float textY) throws IOException {
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, textY);
        cs.showText(text);
        cs.endText();
    }

    float textWidth(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** Word-wrap text to fit within maxWidth pixels using the given font/size */
    List<String> wrap(String text, PDType1Font font, float size, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, size, test) > maxWidth) {
                if (!line.isEmpty()) result.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }
}
