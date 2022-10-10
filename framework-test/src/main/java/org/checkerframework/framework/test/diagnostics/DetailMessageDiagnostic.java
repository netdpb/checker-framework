package org.checkerframework.framework.test.diagnostics;

import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.DOTALL;
import static java.util.stream.Collectors.toList;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Information about a reported diagnostic.
 *
 * <p>Checker Framework uses a special format to put parseable information about a diagnostic into
 * the message text. This object represents that information directly.
 */
public class DetailMessageDiagnostic extends TestDiagnostic {
  /** Parser for the output for -Adetailedmsgtext. */
  // Implemented here: org.checkerframework.framework.source.SourceChecker#detailedMsgTextPrefix
  private static final Pattern DETAIL_MESSAGE_PATTERN =
      Pattern.compile(
          new StringJoiner(" \\$\\$ ")
              .add("(?<file>\\S+):(?<lineNumber>\\d+): error: \\((?<messageKey>[^)]+)\\)")
              .add("(?<messagePartCount>\\d+)")
              .add("(?<messageParts>.*)")
              .toString(),
          DOTALL);

  private static final Pattern OFFSETS_PATTERN =
      Pattern.compile("(\\( (?<start>-?\\d+), (?<end>-?\\d+) \\))?");

  /** The path to the source file containing the diagnostic. */
  private final Path file;

  /** The line number (1-based) of the diagnostic in the {@link #file}. */
  private final int lineNumber;

  /** The message key for the user-visible text message that is emitted. */
  private final String messageKey;

  /** The user-visible message emitted for the diagnostic. */
  private final String message;

  /**
   * Returns an object parsed from a diagnostic message, or {@code null} if the message doesn't
   * match the expected format.
   */
  static @Nullable DetailMessageDiagnostic parse(String input) {
    Matcher matcher = DETAIL_MESSAGE_PATTERN.matcher(input);
    if (!matcher.matches()) {
      return null;
    }

    int messagePartCount = parseInt(matcher.group("messagePartCount"));
    List<String> messageParts =
        Arrays.stream(matcher.group("messageParts").split("\\$\\$", messagePartCount + 2))
            .map(String::trim)
            .collect(toList());
    List<String> messageArguments = messageParts.subList(0, messagePartCount);
    String message = messageParts.get(messagePartCount + 1);

    Matcher offsets = OFFSETS_PATTERN.matcher(messageParts.get(messagePartCount));
    if (!offsets.matches()) throw new IllegalArgumentException("unparseable offsets: " + input);

    return create(
        Paths.get(matcher.group("file")),
        parseInt(matcher.group("lineNumber")),
        matcher.group("messageKey"),
        messageArguments,
        message);
  }

  private static DetailMessageDiagnostic create(
      Path file, int lineNumber, String messageKey, List<String> messageArguments, String message) {
    switch (messageKey) {
      case "assignment":
      case "return":
      case "argument":
      case "type.argument":
        return new ConversionDiagnostic(file, lineNumber, messageKey, messageArguments, message);

      default:
        return new DetailMessageDiagnostic(file, lineNumber, messageKey, message);
    }
  }

  public String getMessageKey() {
    return messageKey;
  }

  public static final class ConversionDiagnostic extends DetailMessageDiagnostic {

    private final String sourceType;
    private final String sinkType;

    private ConversionDiagnostic(
        Path file,
        int lineNumber,
        String messageKey,
        List<String> messageArguments,
        String message) {
      super(file, lineNumber, messageKey, message);
      switch (messageKey) {
        case "assignment":
          this.sourceType = messageArguments.get(0);
          this.sinkType = messageArguments.get(1);
          break;
        case "return":
          this.sourceType = messageArguments.get(1);
          this.sinkType = messageArguments.get(2);
          break;
        case "argument":
        case "type.argument":
          this.sourceType = messageArguments.get(2);
          this.sinkType = messageArguments.get(3);
          break;

        default:
          throw new IllegalArgumentException("unexpected message key: " + messageKey);
      }
    }

    public String getSourceType() {
      return fixTypeString(sourceType);
    }

    public String getSinkType() {
      return fixTypeString(sinkType);
    }

    private static String fixTypeString(String typeString) {
      return Pattern.compile("\\bcapture#\\d+ of \\?")
          .matcher(typeString)
          .replaceAll("capture of ?");
    }
  }

  private DetailMessageDiagnostic(Path file, int lineNumber, String messageKey, String message) {
    super(file.getFileName().toString(), lineNumber, DiagnosticKind.Error, message, false, true);
    this.file = file;
    this.lineNumber = lineNumber;
    this.messageKey = messageKey;
    this.message = message;
  }

  @Override
  public String toString() {
    return String.format("%s:%d: (%s) %s", file, lineNumber, messageKey, message);
  }
}
