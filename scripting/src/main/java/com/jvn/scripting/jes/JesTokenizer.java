package com.jvn.scripting.jes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class JesTokenizer {
  private final String src;
  private final int n;
  private int i = 0;
  private int line = 1;
  private int col = 1;

  public JesTokenizer(String src) {
    this.src = src == null ? "" : src;
    this.n = this.src.length();
  }

  public static List<JesToken> tokenize(InputStream in) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
      StringBuilder sb = new StringBuilder();
      String s;
      while ((s = br.readLine()) != null) sb.append(s).append('\n');
      return new JesTokenizer(sb.toString()).tokenize();
    }
  }

  public List<JesToken> tokenize() {
    List<JesToken> out = new ArrayList<>();
    while (true) {
      skipWs();
      if (i >= n) { out.add(tok(JesTokenType.EOF, "")); break; }
      char c = src.charAt(i);
      if (Character.isLetter(c) || c == '_' ) {
        out.add(ident());
      } else if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(src.charAt(i+1)))) {
        out.add(number());
      } else if (c == '"') {
        out.add(string());
      } else {
        switch (c) {
          case '{': out.add(tok(JesTokenType.LBRACE, "{")); step(); break;
          case '}': out.add(tok(JesTokenType.RBRACE, "}")); step(); break;
          case ':': out.add(tok(JesTokenType.COLON, ":")); step(); break;
          case ',': out.add(tok(JesTokenType.COMMA, ",")); step(); break;
          case '(': out.add(tok(JesTokenType.LPAREN, "(")); step(); break;
          case ')': out.add(tok(JesTokenType.RPAREN, ")")); step(); break;
          default:
            throw new JesParseException("Unexpected character '" + c + "'", line, col);
        }
      }
    }
    return out;
  }

  private JesToken ident() {
    int start = i;
    int startCol = col;
    while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_' || src.charAt(i)=='.')) stepNoTrack();
    String lex = src.substring(start, i);
    return new JesToken(JesTokenType.IDENT, lex, line, startCol);
  }

  private JesToken number() {
    int start = i; int startCol = col;
    if (src.charAt(i) == '-') stepNoTrack();
    while (i < n && Character.isDigit(src.charAt(i))) stepNoTrack();
    if (i < n && src.charAt(i)=='.') { stepNoTrack(); while (i < n && Character.isDigit(src.charAt(i))) stepNoTrack(); }
    return new JesToken(JesTokenType.NUMBER, src.substring(start, i), line, startCol);
  }

  private JesToken string() {
    int startCol = col; stepNoTrack(); // skip opening quote
    StringBuilder sb = new StringBuilder();
    while (i < n && src.charAt(i) != '"') {
      char c = src.charAt(i++);
      col++;
      if (c == '\\' && i < n) {
        char nx = src.charAt(i++); col++;
        if (nx == 'n') sb.append('\n');
        else if (nx == 't') sb.append('\t');
        else sb.append(nx);
      } else sb.append(c);
    }
    if (i < n && src.charAt(i) == '"') {
      stepNoTrack();
      return new JesToken(JesTokenType.STRING, sb.toString(), line, startCol);
    }
    throw new JesParseException("Unterminated string literal", line, startCol);
  }

  private void skipWs() {
    while (i < n) {
      char c = src.charAt(i);
      if (c == '\n') { i++; line++; col = 1; }
      else if (Character.isWhitespace(c)) { i++; col++; }
      else if (c=='/' && i+1<n && src.charAt(i+1)=='/') { while (i<n && src.charAt(i)!='\n') { i++; } }
      else break;
    }
  }

  private JesToken tok(JesTokenType t, String lex) { return new JesToken(t, lex, line, col); }
  private void step() { i++; col++; }
  private void stepNoTrack() { i++; col++; }
}
