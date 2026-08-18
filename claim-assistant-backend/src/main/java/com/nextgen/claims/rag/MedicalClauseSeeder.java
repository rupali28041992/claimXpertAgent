package com.nextgen.claims.rag;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seeds medical insurance policy clauses into MongoDB once at startup.
 * Skipped if MEDICAL clauses already exist (idempotent).
 * Each clause gets a vector embedding via EmbeddingModel so the RAG
 * retriever can rank them by cosine similarity at claim-investigation time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalClauseSeeder {

    private final PolicyClauseVectorRepository repository;
    private final EmbeddingModel embeddingModel;

    private static final String PRODUCT = "MEDICAL";

    @PostConstruct
    void seed() {
        try {
            seedInternal();
        } catch (Exception ex) {
            log.warn("Medical clause seeding skipped — embedding model unavailable ({}). " +
                     "Restart the application with Ollama running to seed clauses.", ex.getMessage());
        }
    }

    private void seedInternal() {
        if (!repository.findByProductType(PRODUCT).isEmpty()) {
            log.info("Medical policy clauses already seeded — skipping.");
            return;
        }

        List<RawClause> clauses = List.of(

            new RawClause("Section 1.1 – Scope of Cover",
                "This policy covers reasonable and customary medical expenses incurred by the insured " +
                "as a result of illness, disease, or bodily injury first occurring and diagnosed during " +
                "the policy period. Cover includes in-patient hospitalisation, day-care procedures, " +
                "pre- and post-hospitalisation expenses, and domiciliary treatment where specified."),

            new RawClause("Section 1.2 – Eligible Expenses",
                "Eligible medical expenses include: room and board charges up to the sub-limit specified " +
                "in the schedule; surgeon, anaesthetist, and specialist fees; diagnostic tests, X-rays, " +
                "and laboratory charges directly related to the admitted condition; medicines and " +
                "consumables prescribed during hospitalisation; and ICU charges up to 2× the room " +
                "sub-limit per day."),

            new RawClause("Section 2.1 – Pre-existing Conditions",
                "Pre-existing conditions are defined as any illness, disease, or injury for which the " +
                "insured received treatment, diagnosis, medical advice, or consultation within 48 months " +
                "prior to policy inception. Pre-existing conditions are excluded from cover during the " +
                "first 36 months of continuous policy coverage. After 36 months of uninterrupted renewal, " +
                "pre-existing conditions become fully covered."),

            new RawClause("Section 2.2 – Undisclosed Pre-existing Conditions",
                "Failure to disclose a pre-existing condition at the time of proposal renders the claim " +
                "voidable at the insurer's discretion. If non-disclosure is established, the insurer may " +
                "repudiate the claim and cancel the policy with refund of pro-rata premium only. " +
                "Deliberate concealment constitutes fraud and no premium shall be refunded."),

            new RawClause("Section 3.1 – Waiting Period – General Illnesses",
                "A 30-day waiting period applies from the policy inception date for all claims arising " +
                "from illness or disease, excluding accidental injuries. No benefit is payable for any " +
                "illness-related hospitalisation commencing within the first 30 days. This waiting period " +
                "is waived for renewals without a break in cover."),

            new RawClause("Section 3.2 – Waiting Period – Specific Procedures",
                "A 24-month waiting period applies to the following conditions and procedures regardless " +
                "of when symptoms first appeared: cataract surgery, hernia repair, knee and joint " +
                "replacement, hysterectomy, gallbladder removal (cholecystectomy), kidney stones, " +
                "tonsillectomy, appendectomy (unless emergency), varicose veins, and piles. Claims for " +
                "these procedures submitted before 24 months of continuous cover will be declined."),

            new RawClause("Section 3.3 – Waiting Period – Maternity",
                "Maternity benefits, including normal delivery, caesarean section, and new-born cover, " +
                "are subject to a 9-month waiting period from the first policy inception date. Claims " +
                "for delivery or pregnancy complications occurring within 9 months of first cover will " +
                "not be admissible. New-born cover commences from day 1 of birth if the mother is an " +
                "insured member at the time of delivery."),

            new RawClause("Section 4.1 – In-patient Hospitalisation",
                "In-patient hospitalisation benefit is payable when the insured is admitted as an " +
                "in-patient for a minimum continuous period of 24 hours. Admission must be on the " +
                "advice of a registered medical practitioner. The insurer will reimburse or settle " +
                "directly (cashless) the eligible expenses up to the sum insured for the policy year, " +
                "subject to applicable deductibles, co-payments, and sub-limits."),

            new RawClause("Section 4.2 – ICU and Critical Care",
                "Intensive Care Unit (ICU) charges are covered up to twice the applicable daily room " +
                "rent sub-limit. If the insured is admitted to ICU for fewer than 24 hours, charges " +
                "are payable on actual basis provided the treating doctor certifies medical necessity. " +
                "Ventilator charges, monitoring fees, and nursing care within ICU are included."),

            new RawClause("Section 4.3 – Room Rent Sub-limits",
                "Room rent is covered up to the limit stated in the policy schedule. If the insured " +
                "opts for a room category exceeding the eligible limit, all associated charges " +
                "(specialist fees, procedure charges, nursing care) shall be proportionally reduced " +
                "on a pro-rata basis. The insured is responsible for the excess room-rent difference " +
                "and all proportionate charges arising from room upgrade."),

            new RawClause("Section 5.1 – Day-Care Procedures",
                "Day-care procedures that do not require 24-hour hospitalisation due to advancement " +
                "in medical technology are covered in full subject to the sum insured. The insurer " +
                "maintains an approved list of day-care procedures including but not limited to: " +
                "chemotherapy, dialysis, lithotripsy, angiography, endoscopy, and radiation therapy. " +
                "Admission must be at a recognised day-care centre or hospital."),

            new RawClause("Section 6.1 – Pre-hospitalisation Expenses",
                "Medical expenses incurred up to 60 days prior to the date of hospitalisation are " +
                "covered, provided they are for the same illness or injury that necessitated " +
                "hospitalisation. Eligible pre-hospitalisation expenses include specialist consultation " +
                "fees, diagnostic tests (blood tests, imaging), and medicines prescribed by the " +
                "consulting specialist directly related to the admitted condition."),

            new RawClause("Section 6.2 – Post-hospitalisation Expenses",
                "Medical expenses incurred up to 90 days after the date of discharge from hospital " +
                "are covered for the same condition. Eligible post-hospitalisation expenses include " +
                "follow-up specialist consultations, prescribed medicines, physiotherapy sessions " +
                "recommended by the treating doctor, and diagnostic tests confirming recovery " +
                "or monitoring the condition. Expenses must be supported by discharge summary."),

            new RawClause("Section 7.1 – Permanent Exclusions",
                "The following are permanently excluded regardless of policy tenure: cosmetic or " +
                "aesthetic treatments including rhinoplasty, liposuction, and hair transplant; " +
                "self-inflicted injuries or attempted suicide; treatment for alcoholism or drug abuse; " +
                "experimental or unproven treatments not recognised by the Indian Medical Council; " +
                "dental treatment except necessitated by accidental injury; optical correction including " +
                "spectacles and contact lenses; infertility and assisted reproduction; and gender " +
                "reassignment surgery."),

            new RawClause("Section 7.2 – War and Nuclear Exclusions",
                "No benefit is payable for any hospitalisation or medical expense arising directly or " +
                "indirectly from war, invasion, act of foreign enemy, hostilities, civil war, military " +
                "coup, nuclear radiation, radioactive contamination, or ionising radiation from nuclear " +
                "fuel or waste. This exclusion applies regardless of whether war has been formally declared."),

            new RawClause("Section 8.1 – Cashless Claim Process",
                "For cashless treatment at a network hospital, the insured must: present the health " +
                "insurance card and a valid photo ID at the hospital's insurance desk at the time of " +
                "admission; obtain pre-authorisation from the insurer's Third-Party Administrator (TPA) " +
                "for planned hospitalisation at least 48 hours in advance; and for emergency admissions, " +
                "notify the TPA within 24 hours of admission. Failure to notify within 24 hours may " +
                "result in the claim being processed as reimbursement only."),

            new RawClause("Section 8.2 – Reimbursement Claim Process",
                "For reimbursement claims, the insured must submit the following documents within " +
                "30 days of discharge: duly completed claim form; original hospital bills and receipts; " +
                "discharge summary signed by the treating doctor; investigation reports including blood " +
                "tests and imaging; prescription copies; pharmacy bills; and indoor case papers if " +
                "requested. Claims submitted after 30 days may be accepted with a written explanation " +
                "of delay; claims beyond 90 days will not be entertained."),

            new RawClause("Section 8.3 – Required Claim Documents",
                "Mandatory documents for all medical claims: (1) Completed and signed claim form; " +
                "(2) Original discharge summary with diagnosis, treatment, and doctor's signature; " +
                "(3) Original itemised hospital bills and payment receipts; (4) Doctor's prescription " +
                "for medicines and tests; (5) All diagnostic investigation reports (blood, urine, " +
                "X-ray, MRI, CT scan, ECG, etc.); (6) Photo ID of the patient; (7) Policy copy or " +
                "health card. For surgery claims: operation theatre notes and implant invoices if applicable."),

            new RawClause("Section 9.1 – Network Hospital Benefits",
                "Treatment at a network hospital entitles the insured to cashless facility subject to " +
                "policy terms. The insurer's network comprises over 5,000 hospitals across India. " +
                "Network hospitals have agreed tariff rates with the insurer, ensuring the insured " +
                "is not charged beyond the network rate for eligible procedures. The current network " +
                "list is available on the insurer's website and the TPA portal."),

            new RawClause("Section 9.2 – Non-Network Hospital Treatment",
                "Treatment at a non-network hospital is admissible on reimbursement basis only. " +
                "The insurer will process the claim against actual bills subject to reasonable and " +
                "customary charges for the geographical region and type of treatment. An additional " +
                "10% co-payment applies to claims from non-network hospitals unless the hospitalisation " +
                "was necessitated by an emergency and no network hospital was available within " +
                "a 15 km radius."),

            new RawClause("Section 10.1 – Sum Insured Restoration",
                "If the sum insured is exhausted during the policy year due to one or more claims, " +
                "it shall be restored once by 100% of the original sum insured at no additional premium. " +
                "The restored sum insured applies only to claims arising from a different illness or " +
                "injury than the one(s) that exhausted the original sum insured. Restoration does not " +
                "apply to the same illness or same continuation of treatment."),

            new RawClause("Section 10.2 – No Claim Bonus",
                "For every claim-free policy year, the sum insured shall be increased by 5% (cumulative " +
                "No Claim Bonus) up to a maximum of 50% of the original sum insured, at no additional " +
                "premium. In the event of a claim, the accumulated No Claim Bonus is reduced by 10% " +
                "of the original sum insured. The base sum insured is never reduced below the original " +
                "amount stated in the schedule.")
        );

        log.info("Seeding {} medical policy clauses into MongoDB...", clauses.size());

        for (RawClause rc : clauses) {
            float[] raw = embeddingModel.embed(rc.section + ". " + rc.text);
            List<Double> embedding = new java.util.ArrayList<>(raw.length);
            for (float v : raw) embedding.add((double) v);

            repository.save(PolicyClauseVector.builder()
                    .id(UUID.randomUUID().toString())
                    .productType(PRODUCT)
                    .section(rc.section)
                    .clauseText(rc.text)
                    .embedding(embedding)
                    .build());
        }

        log.info("Medical clause seeding complete — {} clauses stored.", clauses.size());
    }  // end seedInternal

    private record RawClause(String section, String text) {}
}
