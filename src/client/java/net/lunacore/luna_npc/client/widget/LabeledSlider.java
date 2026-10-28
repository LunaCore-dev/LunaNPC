package net.lunacore.luna_npc.client.widget;

import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

// The standard LunaNPC single-value slider: optional label prefix, a formatter for the shown
// value, and a change callback. setValue lets screens re-point the slider at another value.
public class LabeledSlider extends AbstractSliderButton {

    // Maps the slider position (0..1) to the shown value and back. Non-linear controls (like
    // the speed slider that spends 80% of its travel on the everyday low range) plug in their
    // own curve; both functions must be exact inverses of each other.
    public record Curve(DoubleUnaryOperator positionToValue, DoubleUnaryOperator valueToPosition) {

        public static Curve linear(float min, float max) {
            return new Curve(position -> min + position * (max - min),
                    value -> (value - min) / (max - min));
        }

        // Low-heavy: the first 80% of the slider's travel covers only the bottom 10% of the range (fine
        // control over the common low values), the last 20% ramps up to the max. So a 0..1000 field
        // spends most of its length near zero, where values are usually set.
        public static Curve lowBiased(float min, float max) {
            double span = max - min;
            return new Curve(
                    position -> min + span * (position <= 0.8 ? position / 8.0 : 0.1 + (position - 0.8) * 4.5),
                    value -> {
                        double v = span == 0.0 ? 0.0 : (value - min) / span;
                        return v <= 0.1 ? v * 8.0 : 0.8 + (v - 0.1) * (0.2 / 0.9);
                    });
        }
    }

    private final Component label;
    private final Curve curve;
    private final Function<Float, String> formatter;
    private final Consumer<Float> onChange;

    public LabeledSlider(int x, int y, int width, int height, Component label, float min, float max,
                         float initial, Function<Float, String> formatter, Consumer<Float> onChange) {
        this(x, y, width, height, label, Curve.linear(min, max), initial, formatter, onChange);
    }

    public LabeledSlider(int x, int y, int width, int height, Component label, Curve curve,
                         float initial, Function<Float, String> formatter, Consumer<Float> onChange) {
        super(x, y, width, height, Component.empty(), curve.valueToPosition().applyAsDouble(initial));
        this.label = label;
        this.curve = curve;
        this.formatter = formatter;
        this.onChange = onChange;
        updateMessage();
    }

    public float currentValue() {
        return (float) curve.positionToValue().applyAsDouble(value);
    }

    public void setValue(float newValue) {
        this.value = curve.valueToPosition().applyAsDouble(newValue);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        String formatted = formatter.apply(currentValue());
        setMessage(label.getString().isEmpty()
                ? Component.literal(formatted)
                : label.copy().append(": " + formatted));
    }

    @Override
    protected void applyValue() {
        onChange.accept(currentValue());
    }
}
