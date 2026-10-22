package ir.linuxian.second.service;

import java.util.List;
import java.util.Map;

/**
 * Static dialing-code list for the phone-registration country dropdown.
 * The admin picks the default in Admin → Themes & Settings → Registration.
 */
public final class Countries {

    public static final String FALLBACK_DEFAULT_DIAL = "+98";

    private Countries() {
    }

    public record Country(String code, String dial, String name, String nameFa) {
        public Map<String, String> toMap() {
            return Map.of("code", code, "dial", dial, "name", name, "nameFa", nameFa);
        }
    }

    public static final List<Country> ALL = List.of(
            new Country("IR", "+98", "Iran", "ایران"),
            new Country("AF", "+93", "Afghanistan", "افغانستان"),
            new Country("DE", "+49", "Germany", "آلمان"),
            new Country("US", "+1", "United States", "آمریکا"),
            new Country("CA", "+1", "Canada", "کانادا"),
            new Country("GB", "+44", "United Kingdom", "بریتانیا"),
            new Country("FR", "+33", "France", "فرانسه"),
            new Country("NL", "+31", "Netherlands", "هلند"),
            new Country("SE", "+46", "Sweden", "سوئد"),
            new Country("IT", "+39", "Italy", "ایتالیا"),
            new Country("ES", "+34", "Spain", "اسپانیا"),
            new Country("TR", "+90", "Turkey", "ترکیه"),
            new Country("AZ", "+994", "Azerbaijan", "جمهوری آذربایجان"),
            new Country("AM", "+374", "Armenia", "ارمنستان"),
            new Country("GE", "+995", "Georgia", "گرجستان"),
            new Country("RU", "+7", "Russia", "روسیه"),
            new Country("KZ", "+7", "Kazakhstan", "قزاقستان"),
            new Country("UZ", "+998", "Uzbekistan", "ازبکستان"),
            new Country("TM", "+993", "Turkmenistan", "ترکمنستان"),
            new Country("IQ", "+964", "Iraq", "عراق"),
            new Country("SY", "+963", "Syria", "سوریه"),
            new Country("LB", "+961", "Lebanon", "لبنان"),
            new Country("JO", "+962", "Jordan", "اردن"),
            new Country("SA", "+966", "Saudi Arabia", "عربستان"),
            new Country("AE", "+971", "UAE", "امارات"),
            new Country("QA", "+974", "Qatar", "قطر"),
            new Country("KW", "+965", "Kuwait", "کویت"),
            new Country("BH", "+973", "Bahrain", "بحرین"),
            new Country("OM", "+968", "Oman", "عمان"),
            new Country("YE", "+967", "Yemen", "یمن"),
            new Country("EG", "+20", "Egypt", "مصر"),
            new Country("PK", "+92", "Pakistan", "پاکستان"),
            new Country("IN", "+91", "India", "هند"),
            new Country("CN", "+86", "China", "چین"),
            new Country("MY", "+60", "Malaysia", "مالزی"),
            new Country("AU", "+61", "Australia", "استرالیا")
    );

    public static boolean isKnownDial(String dial) {
        if (dial == null) {
            return false;
        }
        String d = dial.trim();
        return ALL.stream().anyMatch(c -> c.dial().equals(d));
    }
}
