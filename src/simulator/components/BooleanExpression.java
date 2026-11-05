package simulator.components;

import simulator.core.VoltageState;

import java.util.List;

public final class BooleanExpression {
    private interface Term {
        VoltageState evaluate(VoltageState[] inputs);
    }

    private final Term root;
    private final String text;

    private BooleanExpression(Term root, String text) {
        this.root = root;
        this.text = text;
    }

    public static BooleanExpression parse(String text, List<String> inputNames) {
        Parser parser = new Parser(text, inputNames);
        Term root = parser.parseOr();
        parser.skipSpaces();
        if (parser.position < text.length()) {
            throw new IllegalArgumentException("Unexpected '" + text.charAt(parser.position)
                    + "' in expression: " + text);
        }
        return new BooleanExpression(root, text.trim());
    }

    public VoltageState evaluate(VoltageState[] inputs) {
        return root.evaluate(inputs);
    }

    public String getText() {
        return text;
    }

    private static VoltageState not(VoltageState value) {
        if (value == VoltageState.HIGH) return VoltageState.LOW;
        if (value == VoltageState.LOW) return VoltageState.HIGH;
        return VoltageState.UNKNOWN;
    }

    private static VoltageState and(VoltageState left, VoltageState right) {
        if (left == VoltageState.LOW || right == VoltageState.LOW) return VoltageState.LOW;
        if (left == VoltageState.HIGH && right == VoltageState.HIGH) return VoltageState.HIGH;
        return VoltageState.UNKNOWN;
    }

    private static VoltageState or(VoltageState left, VoltageState right) {
        if (left == VoltageState.HIGH || right == VoltageState.HIGH) return VoltageState.HIGH;
        if (left == VoltageState.LOW && right == VoltageState.LOW) return VoltageState.LOW;
        return VoltageState.UNKNOWN;
    }

    private static VoltageState xor(VoltageState left, VoltageState right) {
        if ((left != VoltageState.HIGH && left != VoltageState.LOW)
                || (right != VoltageState.HIGH && right != VoltageState.LOW)) {
            return VoltageState.UNKNOWN;
        }
        return left == right ? VoltageState.LOW : VoltageState.HIGH;
    }

    private static final class Parser {
        private final String text;
        private final List<String> inputNames;
        private int position;

        private Parser(String text, List<String> inputNames) {
            this.text = text;
            this.inputNames = inputNames;
        }

        private void skipSpaces() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private boolean accept(char symbol) {
            skipSpaces();
            if (position < text.length() && text.charAt(position) == symbol) {
                position++;
                return true;
            }
            return false;
        }

        private Term parseOr() {
            Term term = parseXor();
            while (accept('|')) {
                Term left = term;
                Term right = parseXor();
                term = inputs -> or(left.evaluate(inputs), right.evaluate(inputs));
            }
            return term;
        }

        private Term parseXor() {
            Term term = parseAnd();
            while (accept('^')) {
                Term left = term;
                Term right = parseAnd();
                term = inputs -> xor(left.evaluate(inputs), right.evaluate(inputs));
            }
            return term;
        }

        private Term parseAnd() {
            Term term = parseUnary();
            while (accept('&')) {
                Term left = term;
                Term right = parseUnary();
                term = inputs -> and(left.evaluate(inputs), right.evaluate(inputs));
            }
            return term;
        }

        private Term parseUnary() {
            if (accept('!')) {
                Term operand = parseUnary();
                return inputs -> not(operand.evaluate(inputs));
            }
            if (accept('(')) {
                Term inner = parseOr();
                if (!accept(')')) {
                    throw new IllegalArgumentException("Missing ')' in expression: " + text);
                }
                return inner;
            }
            return parseName();
        }

        private Term parseName() {
            skipSpaces();
            int start = position;
            while (position < text.length()
                    && (Character.isLetterOrDigit(text.charAt(position)) || text.charAt(position) == '_')) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("Expected a wire name, 0 or 1 in expression: " + text);
            }
            String name = text.substring(start, position);
            if (name.equals("0")) return inputs -> VoltageState.LOW;
            if (name.equals("1")) return inputs -> VoltageState.HIGH;
            int index = inputNames.indexOf(name);
            if (index < 0) {
                throw new IllegalArgumentException("Expression uses " + name
                        + ", which is not one of this block's inputs: " + String.join(", ", inputNames));
            }
            return inputs -> inputs[index];
        }
    }
}
