/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Mihai Toader, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.util;

import com.goide.psi.GoStringLiteral;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import org.jetbrains.annotations.NotNull;

public class GoStringLiteralEscaper extends LiteralTextEscaper<GoStringLiteral> {

  private int[] outSourceOffsets;

  public GoStringLiteralEscaper(@NotNull GoStringLiteral host) {
    super(host);
  }

  @Override
  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull final StringBuilder outChars) {
    TextRange.assertProperRange(rangeInsideHost);

    String subText = rangeInsideHost.substring(myHost.getText());

    if (myHost.getRawString() != null) {
      outChars.append(subText);
    }
    else {
      Ref<int[]> sourceOffsetsRef = new Ref<int[]>();
      parseStringCharacters(subText, outChars, sourceOffsetsRef);
      outSourceOffsets = sourceOffsetsRef.get();
    }

    return true;
  }

  @Override
  public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    TextRange.assertProperRange(rangeInsideHost);

    if (myHost.getRawString() != null) {
      int offset = offsetInDecoded;
      offset += rangeInsideHost.getStartOffset();
      if (offset < rangeInsideHost.getStartOffset()) offset = rangeInsideHost.getStartOffset();
      if (offset > rangeInsideHost.getEndOffset()) offset = rangeInsideHost.getEndOffset();
      return offset;
    }

    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  public static boolean parseStringCharacters(String chars, StringBuilder outChars, Ref<int[]> sourceOffsetsRef) {
    int[] sourceOffsets = new int[chars.length() + 1];
    sourceOffsetsRef.set(sourceOffsets);

    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      for (int i = 0; i < sourceOffsets.length; i++) {
        sourceOffsets[i] = i;
      }
      return true;
    }
    int index = 0;

    while (index < chars.length()) {
      char c = chars.charAt(index++);

      sourceOffsets[outChars.length()] = index - 1;
      sourceOffsets[outChars.length() + 1] = index;

      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) return false;
      c = chars.charAt(index++);

      switch (c) {
        case 'a':
          outChars.append((char)7);
          break;

        case 'b':
          outChars.append('\b');
          break;

        case 'f':
          outChars.append('\f');
          break;

        case 'n':
          outChars.append('\n');
          break;

        case 'r':
          outChars.append('\r');
          break;

        case 't':
          outChars.append('\t');
          break;

        case 'v':
          outChars.append((char)0x0b);
          break;

        case '\\':
          outChars.append('\\');
          break;

        case '\'':
          outChars.append('\'');
          break;

        case '"':
          outChars.append('"');
          break;

        case '\n':
          outChars.append('\n');
          break;

        // octal
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7': {
          char startC = c;
          int v = (int)c - '0';
          if (index < chars.length()) {
            c = chars.charAt(index++);
            if ('0' <= c && c <= '7') {
              v <<= 3;
              v += c - '0';
              if (startC <= '3' && index < chars.length()) {
                c = chars.charAt(index++);
                if ('0' <= c && c <= '7') {
                  v <<= 3;
                  v += c - '0';
                }
                else {
                  index--;
                }
              }
            }
            else {
              index--;
            }
          }
          outChars.append((char)v);
        }
        break;

        // hex
        case 'x':
          if (index + 2 <= chars.length()) {
            try {
              int v = Integer.parseInt(chars.substring(index, index + 2), 16);
              outChars.append((char)v);
              index += 2;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;

        // little unicode
        case 'u':
          if (index + 4 <= chars.length()) {
            try {
              int v = Integer.parseInt(chars.substring(index, index + 4), 16);
              //line separators are invalid here
              if (v == 0x000a || v == 0x000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)v);
              index += 4;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;

        // big unicode
        case 'U':
          if (index + 8 <= chars.length()) {
            try {
              int v = Integer.parseInt(chars.substring(index, index + 8), 16);
              //line separators are invalid here
              if (v == 0x0000000a || v == 0x0000000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)v);
              index += 4;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;

        default:
          return false;
      }

      sourceOffsets[outChars.length()] = index;
    }
    return true;
  }

  /**
   * Escapes the specified string in accordance with https://golang.org/ref/spec#Rune_literals
   * @param chars
   * @param outChars
   */
  public static void escapeString(@NotNull String chars, @NotNull StringBuilder outChars) {
    int index = 0;

    while (index < chars.length()) {
      int c = chars.codePointAt(index);

      switch (c) {
        case (char)7:
          outChars.append("\\a");
          break;

        case '\b':
          outChars.append("\\b");
          break;

        case '\f':
          outChars.append("\\f");
          break;

        case '\n':
          outChars.append("\\n");
          break;

        case '\r':
          outChars.append("\\r");
          break;

        case '\t':
          outChars.append("\\t");
          break;

        case (char)0x0b:
          outChars.append("\\v");
          break;

        case '\\':
          outChars.append("\\\\");
          break;

        case '\'':
          outChars.append("\\'");
          break;

        case '"':
          outChars.append("\\\"");
          break;

        default:
          switch (Character.getType(c)) {
            case Character.CONTROL:
            case Character.FORMAT:
            case Character.PRIVATE_USE:
            case Character.UNASSIGNED:
              outChars.append("\\u");
              outChars.append(String.format("%04X", c));
              break;

            default:
              outChars.appendCodePoint(c);
          }
      }

      index += Character.charCount(c);
    }
  }

  @Override
  public boolean isOneLine() {
    return true;
  }
}
