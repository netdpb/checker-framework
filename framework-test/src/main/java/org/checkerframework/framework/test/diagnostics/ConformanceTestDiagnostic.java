package org.checkerframework.framework.test.diagnostics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.framework.test.diagnostics.DetailMessageDiagnostic.ConversionDiagnostic;
import org.checkerframework.javacutil.BugInCF;

/** A {@link TestDiagnostic} that represents a JSpecify conformance test assertion. */
public abstract class ConformanceTestDiagnostic extends TestDiagnostic {

  protected ConformanceTestDiagnostic(String filename, long lineNumber, String message) {
    super(filename, lineNumber, DiagnosticKind.JSpecify, message, false, true);
  }

  static ConformanceTestDiagnostic create(
      String filename, long lineNumber, String stringFromJavaFile) {
    Matcher conformanceTestAssertion = TEST.matcher(stringFromJavaFile.trim());
    if (!conformanceTestAssertion.matches()) {
      return null;
    }
    String testKind = conformanceTestAssertion.group("testKind");
    String testArguments = conformanceTestAssertion.group("testArguments");
    switch (testKind) {
      case "cannot-convert":
        return new CannotConvert(testArguments, filename, lineNumber, stringFromJavaFile);

      case "irrelevant_annotation":
        return new IrrelevantAnnotation(filename, lineNumber, stringFromJavaFile);

      default:
        throw new BugInCF(
            String.format(
                "unknown conformance test assertion in %s:%d: %s",
                filename, lineNumber, stringFromJavaFile));
    }
  }

  private static final Pattern TEST =
      Pattern.compile("test:(?<testKind>[^:]+)(:(?<testArguments>.*))?");

  /** Returns {@code true} if this assertion matches the given found diagnostic. */
  public abstract boolean matches(DetailMessageDiagnostic found);

  /** Conformance test assertion of the form {@code test:cannot-convert:sourceType to sinkType}. */
  static final class CannotConvert extends ConformanceTestDiagnostic {

    private final String sourceType;
    private final String sinkType;

    private CannotConvert(String testArguments, String filename, long lineNumber, String message) {
      super(filename, lineNumber, message);
      Matcher types = CANNOT_CONVERT.matcher(testArguments);
      if (!types.matches()) {
        throw new BugInCF(
            String.format("bad assertion in %s:%d: %s", filename, lineNumber, message));
      }
      sourceType = types.group("sourceType");
      sinkType = types.group("sinkType");
    }

    private static final Pattern CANNOT_CONVERT =
        Pattern.compile("(?<sourceType>.*) to (?<sinkType>.*)");

    @Override
    public boolean matches(DetailMessageDiagnostic found) {
      if (!(found instanceof ConversionDiagnostic)) {
        return false;
      }
      ConversionDiagnostic foundConversion = ((ConversionDiagnostic) found);
      return foundConversion.getSourceType().equals(sourceType)
          && foundConversion.getSinkType().equals(sinkType);
    }
  }

  /** Conformance test assertion of the form {@code test:irrelevant_annotation}. */
  static final class IrrelevantAnnotation extends ConformanceTestDiagnostic {

    private IrrelevantAnnotation(String filename, long lineNumber, String message) {
      super(filename, lineNumber, message);
    }

    @Override
    public boolean matches(DetailMessageDiagnostic found) {
      switch (found.getMessageKey()) {
          /*
           * We'd rather avoid this `bound` error (in part because it suggests that the annotation
           * is having some effect, which we don't want!), but the most important thing is that the
           * checker is issuing one or more errors when someone annotates a type-parameter
           * declaration. The second most important thing is that the errors issued include our
           * custom `*.annotated` error. This test probably doesn't confirm that second thing
           * anymore, but I did manually confirm that it is true as of this writing.
           */
        case "bound":
        case "enum.constant.annotated":
        case "local.variable.annotated":
        case "outer.annotated":
        case "primitive.annotated":
        case "type.parameter.annotated":
        case "wildcard.annotated":
          return true;
        default:
          return false;
      }
    }
  }
}
