package simulator.parser;

public final class StandardLibrary {
    public static final String SOURCE = String.join("\n",
            "DEF HALF_ADDER A B SUM CARRY",
            "XOR sum 10 A B SUM",
            "AND carry 10 A B CARRY",
            "ENDDEF",
            "DEF FULL_ADDER A B CIN SUM COUT",
            "WIRE s1",
            "WIRE c1",
            "WIRE c2",
            "HALF_ADDER ha1 A B s1 c1",
            "HALF_ADDER ha2 s1 CIN SUM c2",
            "OR carry 10 c1 c2 COUT",
            "ENDDEF",
            "DEF MUX2 A B SEL Y",
            "FUNC select 10 A B SEL Y = (A & !SEL) | (B & SEL)",
            "ENDDEF",
            "DEF DECODER2 A1 A0 Y0 Y1 Y2 Y3",
            "FUNC d0 10 A1 A0 Y0 = !A1 & !A0",
            "FUNC d1 10 A1 A0 Y1 = !A1 & A0",
            "FUNC d2 10 A1 A0 Y2 = A1 & !A0",
            "FUNC d3 10 A1 A0 Y3 = A1 & A0",
            "ENDDEF");

    private StandardLibrary() { }
}
