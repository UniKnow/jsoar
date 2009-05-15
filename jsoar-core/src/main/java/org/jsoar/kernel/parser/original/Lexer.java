/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 14, 2008
 */
package org.jsoar.kernel.parser.original;

import java.io.IOException;
import java.io.Reader;

import org.jsoar.kernel.parser.PossibleSymbolTypes;
import org.jsoar.kernel.tracing.Printer;

/**
 * 
 * <p>lexer.cpp
 * 
 * <p>The following fields or methods were removed because they were unnecessary or unused:
 * <ul>
 * <li>fake_rparen_at_eol
 * <li>do_fake_rparen
 * <li>load_errors_quit
 * <li>DOLLAR_STRING
 * <li>lex_dollar
 * </ul>
 * @author ray
 */
public class Lexer
{
    /**
     * <p>lexer.h:95:LENGTH_OF_LONGEST_SPECIAL_LEXEME
     */
    private static final int LENGTH_OF_LONGEST_SPECIAL_LEXEME = 3;
    
    private static final char EOF_AS_CHAR = 0xffff;

    private final Printer printer;
    
    private Reader input;

    private char current_char;

    private int current_column = 0;

    private int current_line = 0;

    @SuppressWarnings("unused")
    private int column_of_start_of_last_lexeme = 0;

    @SuppressWarnings("unused")
    private int line_of_start_of_last_lexeme = 0;

    private boolean allow_ids;

    private int parentheses_level = 0;

    private Lexeme lexeme = new Lexeme();

    private static final LexerRoutine lex_unknown = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            lexer.printer.error("Unknown character encountered '%c'", lexer.current_char);
            // TODO handle error
            lexer.current_char = EOF_AS_CHAR;
        }
    };

    private LexerRoutine lex_eof = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            lexer.store_and_advance();
            lexer.setLexemeType(LexemeType.EOF);
        }
    };

    private LexerRoutine lex_equal = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            // Lexeme might be "=", or symbol
            // Note: this routine relies on = being a constituent character
            lexer.read_constituent_string();
            if (lexer.lexeme.length() == 1)
            {
                lexer.lexeme.type = LexemeType.EQUAL;
            }
            else
            {
                lexer.determine_type_of_constituent_string();
            }
        }
    };

    private static LexerRoutine lex_ampersand = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            // Lexeme might be "&", or symbol
            // Note: this routine relies on & being a constituent character
            lexer.read_constituent_string();
            if (lexer.lexeme.length() == 1)
            {
                lexer.lexeme.type = LexemeType.AMPERSAND;
            }
            else
            {
                lexer.determine_type_of_constituent_string();
            }
        }
    };

    private static LexerRoutine lex_lparen = new BasicLexerRoutine(LexemeType.L_PAREN)
    {
        public void lex(Lexer lexer) throws IOException
        {
            super.lex(lexer);
            lexer.parentheses_level++;
        }
    };

    private static LexerRoutine lex_rparen = new BasicLexerRoutine(LexemeType.R_PAREN)
    {
        public void lex(Lexer lexer) throws IOException
        {
            super.lex(lexer);
            if (lexer.parentheses_level > 0)
            {
                lexer.parentheses_level--;
            }
        }
    };

    private static LexerRoutine lex_greater = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            // Lexeme might be ">", ">=", ">>", or symbol
            // Note: this routine relies on =,> being constituent characters
            lexer.read_constituent_string();
            if (lexer.lexeme.length() == 1)
            {
                lexer.lexeme.type = LexemeType.GREATER;
                return;
            }
            if (lexer.lexeme.length() == 2)
            {
                if (lexer.lexeme.at(1) == '>')
                {
                    lexer.lexeme.type = LexemeType.GREATER_GREATER;
                    return;
                }
                if (lexer.lexeme.at(1) == '=')
                {
                    lexer.lexeme.type = LexemeType.GREATER_EQUAL;
                    return;
                }
            }
            lexer.determine_type_of_constituent_string();
        }
    };

    private static LexerRoutine lex_less = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            // Lexeme might be "<", "<=", "<=>", "<>", "<<", or variable
            // Note: this routine relies on =,<,> being constituent characters
            lexer.read_constituent_string();
            if (lexer.lexeme.length() == 1)
            {
                lexer.lexeme.type = LexemeType.LESS;
                return;
            }
            if (lexer.lexeme.length() == 2)
            {
                if (lexer.lexeme.at(1) == '>')
                {
                    lexer.lexeme.type = LexemeType.NOT_EQUAL;
                    return;
                }
                if (lexer.lexeme.at(1) == '=')
                {
                    lexer.lexeme.type = LexemeType.LESS_EQUAL;
                    return;
                }
                if (lexer.lexeme.at(1) == '<')
                {
                    lexer.lexeme.type = LexemeType.LESS_LESS;
                    return;
                }
            }
            if (lexer.lexeme.length() == 3)
            {
                if (lexer.lexeme.at(1) == '='
                        && lexer.lexeme.at(2) == '>')
                {
                    lexer.lexeme.type = LexemeType.LESS_EQUAL_GREATER;
                    return;
                }
            }
            lexer.determine_type_of_constituent_string();
        }
    };

    private static LexerRoutine lex_period = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            lexer.store_and_advance();
            // if we stopped at '.', it might be a floating-point number, so
            // be careful to check for this case ---
            if (Character.isDigit(lexer.current_char))
            {
                lexer.read_rest_of_floating_point_number();
            }
            if (lexer.lexeme.length() == 1)
            {
                lexer.lexeme.type = LexemeType.PERIOD;
                return;
            }
            lexer.determine_type_of_constituent_string();
        }
    };

    private static LexerRoutine lex_plus = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            // Lexeme might be +, number, or symbol
            // Note: this routine relies on various things being constituent chars

            lexer.read_constituent_string();
            // if we stopped at '.', it might be a floating-point number, so
            // be careful to check for this case
            if (lexer.current_char == '.')
            {
                boolean could_be_floating_point = true;
                for (int i = 1; i < lexer.lexeme.length(); i++)
                {
                    if (!Character.isDigit(lexer.lexeme.at(i)))
                    {
                        could_be_floating_point = false;
                    }
                }
                if (could_be_floating_point)
                {
                    lexer.read_rest_of_floating_point_number();
                }
            }
            if (lexer.lexeme.length() == 1)
            {
                lexer.lexeme.type = LexemeType.PLUS;
                return;
            }
            lexer.determine_type_of_constituent_string();
        }
    };

    private static LexerRoutine lex_minus = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            // Lexeme might be -, -->, number, or symbol
            // Note: this routine relies on various things being constituent chars
            boolean could_be_floating_point;

            lexer.read_constituent_string();
            // if we stopped at '.', it might be a floating-point number, so
            // be careful to check for this case
            if (lexer.current_char == '.')
            {
                could_be_floating_point = true;
                for (int i = 1; i < lexer.lexeme.length(); i++)
                {
                    if (!Character.isDigit(lexer.lexeme.at(i)))
                    {
                        could_be_floating_point = false;
                    }
                }
                if (could_be_floating_point)
                {
                    lexer.read_rest_of_floating_point_number();
                }
            }
            if (lexer.lexeme.length() == 1)
            {
                lexer.lexeme.type = LexemeType.MINUS;
                return;
            }
            if (lexer.lexeme.length() == 3)
            {
                if ((lexer.lexeme.at(1) == '-') && (lexer.lexeme.at(2) == '>'))
                {
                    lexer.lexeme.type = LexemeType.RIGHT_ARROW;
                    return;
                }
            }
            lexer.determine_type_of_constituent_string();
        }
    };

    private static LexerRoutine lex_digit = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            boolean could_be_floating_point;

            lexer.read_constituent_string();
            // if we stopped at '.', it might be a floating-point number, so
            // be careful to check for this case
            if (lexer.current_char == '.')
            {
                could_be_floating_point = true;
                for (int i = 1; i < lexer.lexeme.length(); i++)
                {
                    if (!Character.isDigit(lexer.lexeme.at(i)))
                    {
                        could_be_floating_point = false;
                    }
                }
                if (could_be_floating_point)
                {
                    lexer.read_rest_of_floating_point_number();
                }
            }
            lexer.determine_type_of_constituent_string();
        }
    };

    private static LexerRoutine lex_constituent_string = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            lexer.read_constituent_string();
            lexer.determine_type_of_constituent_string();
        }
    };

    private static LexerRoutine lex_vbar = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            lexer.lexeme.type = LexemeType.SYM_CONSTANT;
            lexer.get_next_char();
            do
            {
                if ((lexer.current_char == EOF_AS_CHAR))
                {
                    lexer.printer.print ("Error: opening '|' without closing '|'\n");
                    lexer.print_location_of_most_recent_lexeme();
                    lexer.lexeme.type = LexemeType.EOF;
                    return;
                }
                if (lexer.current_char == '\\')
                {
                    lexer.get_next_char();
                    lexer.lexeme.string += lexer.current_char;
                    lexer.get_next_char();
                }
                else if (lexer.current_char == '|')
                {
                    lexer.get_next_char();
                    break;
                }
                else
                {
                    lexer.lexeme.string += lexer.current_char;
                    lexer.get_next_char();
                }
            } while (true);
        }
    };

    private static LexerRoutine lex_quote = new LexerRoutine()
    {
        public void lex(Lexer lexer) throws IOException
        {
            lexer.lexeme.type = LexemeType.QUOTED_STRING;
            lexer.get_next_char();
            do
            {
                if ((lexer.current_char == EOF_AS_CHAR))
                {

                    lexer.printer.print ("Error: opening '\"' without closing '\"'\n");
                    lexer.print_location_of_most_recent_lexeme();
                    lexer.lexeme.type = LexemeType.EOF;
                    return;
                }
                if (lexer.current_char == '\\')
                {
                    lexer.get_next_char();
                    lexer.lexeme.string += lexer.current_char;
                    lexer.get_next_char();
                }
                else if (lexer.current_char == '"')
                {
                    lexer.get_next_char();
                    break;
                }
                else
                {
                    lexer.lexeme.string += lexer.current_char;
                    lexer.get_next_char();
                }
            } while (true);
        }
    };

    private final LexerRoutine[] lexer_routines = new LexerRoutine[256];
    static final boolean constituent_char[] = new boolean[256];
    static 
    {
        final String extra_constituents = "$%&*+-/:<=>?_";
        for (int i = 0; i < constituent_char.length; i++)
        {
            // When i == 1, strchr returns true based on the terminating
            // character. This is not the intent, so we exclude that case here.
            if (i != 0 && extra_constituents.indexOf((char) i) != -1)
            {
                constituent_char[i] = true;
            }
            else
            {
                constituent_char[i] = Character.isLetterOrDigit((char) i);
            }
        }
    }
    
    private static boolean isConstituentChar(char c)
    {
        return c < constituent_char.length && constituent_char[c];
    }

    static final boolean number_starters[] = new boolean[256];
    static
    {
        for (int i = 0; i < number_starters.length; i++)
        {
            switch (i)
            {
            case '+': number_starters[i] = true; break;
            case '-': number_starters[i] = true; break;
            case '.': number_starters[i] = true; break;
            default:
                number_starters[i] = Character.isDigit((char) i);
            }
        }
    }

    public Lexer(Printer printer, Reader reader) throws IOException
    {
        this.printer = printer;
        this.input = reader;
        init_lexer();
        get_next_char();
    }
    
    Printer getPrinter()
    {
        return printer;
    }
    
    public Lexeme getCurrentLexeme()
    {
        return lexeme;
    }

    public boolean isEof()
    {
        return lexeme.type == LexemeType.EOF;
    }
    
    /**
     * Get_next_char() gets the next character from the current input file and
     * puts it into the agent variable current_char.
     * 
     * <p>lexer.cpp::get_next_char
     * 
     * @throws IOException
     */
    private void get_next_char() throws IOException
    {
        if (current_char == EOF_AS_CHAR)
        {
            return;
        }

        int next_char = input.read();
        current_char = next_char >= 0 ? (char) next_char : EOF_AS_CHAR;
    }

    private void record_position_of_start_of_lexeme()
    {
        column_of_start_of_last_lexeme = current_column - 1;
        line_of_start_of_last_lexeme = current_line;
    }

    public void setLexemeType(LexemeType type)
    {
        lexeme.type = type;
    }

    void store_and_advance() throws IOException
    {
        lexeme.append(current_char);
        get_next_char();
    }

    void read_constituent_string() throws IOException
    {
        while ((current_char != EOF_AS_CHAR) && isConstituentChar(current_char))
        {
            store_and_advance();
        }
    }

    void read_rest_of_floating_point_number() throws IOException
    {
        // at entry, current_char=="."; we read the "." and rest of number
        store_and_advance();

        while (Character.isDigit(current_char))
        {
            store_and_advance(); // string of digits
        }
        if ((current_char == 'e') || (current_char == 'E'))
        {
            store_and_advance(); // E
            if ((current_char == '+') || (current_char == '-'))
            {
                store_and_advance(); // optional leading + or -
            }
            while (Character.isDigit(current_char))
            {
                store_and_advance(); // string of digits
            }
        }
    }

    private boolean determine_type_of_constituent_string()
    {
        PossibleSymbolTypes possibleType = determine_possible_symbol_types_for_string(lexeme.string);

        // check whether it's a variable
        if (possibleType.possible_var)
        {
            lexeme.type = LexemeType.VARIABLE;
            return true;
        }

        // check whether it's an integer
        if (possibleType.possible_ic)
        {
            try
            {
                lexeme.type = LexemeType.INTEGER;
                lexeme.int_val = Integer.valueOf(lexeme.string);
            }
            catch (NumberFormatException e)
            {
                printer.print("Error: bad integer '" + lexeme.string + "' (probably too large)\n");
                print_location_of_most_recent_lexeme();
                lexeme.int_val = 0;
                return false;
            }
            return true;
        }

        // check whether it's a floating point number
        if (possibleType.possible_fc)
        {
            try
            {
                lexeme.type = LexemeType.FLOAT;
                lexeme.float_val = Double.valueOf(lexeme.string);
            }
            catch (NumberFormatException e)
            {
                printer.print("Error: bad floating point number: '" + lexeme.string + "\n");
                print_location_of_most_recent_lexeme();
                lexeme.float_val = 0.0f;
                return false;
            }
            return true;
        }

        // check if it's an identifier
        if (allow_ids && possibleType.possible_id)
        {
            try
            {
                lexeme.id_letter = Character.toUpperCase(lexeme.string
                        .charAt(0));
                lexeme.type = LexemeType.IDENTIFIER;
                lexeme.id_number = Integer.valueOf(lexeme.string.substring(1));
            }
            catch (NumberFormatException e)
            {
                printer.print ("Error: bad number for identifier (probably too large)\n");
                print_location_of_most_recent_lexeme();
                lexeme.id_number = 0;
                return false;
            }
            return true;
        }

        // otherwise it must be a symbolic constant
        if (possibleType.possible_sc)
        {
            lexeme.type = LexemeType.SYM_CONSTANT;
            if (printer.isPrintWarnings())
            {
                if (lexeme.at(0) == '<')
                {
                    if (lexeme.at(1) == '<')
                    {
                        printer.print( 
                           "Warning: Possible disjunctive encountered in reading symbolic constant\n" +
                           " If a disjunctive was intended, add a space after <<\n" +
                           " If a constant was intended, surround constant with vertical bars\n");
                        //
                        // xml_generate_warning(thisAgent, "Warning: Possible
                        // disjunctive encountered in reading symbolic
                        // constant.\n If a disjunctive was intended, add a
                        // space after &lt;&lt;\n If a constant was intended,
                        // surround constant with vertical bars.");
                        // TODO: should this be appended to previous XML
                        // message, or should it be a separate message?
                        print_location_of_most_recent_lexeme();
                    }
                    else
                    {
                        printer.print(
                           "Warning: Possible variable encountered in reading symbolic constant\n" +
                           " If a constant was intended, surround constant with vertical bars\n");

                        // TODO
                        // xml_generate_warning(thisAgent, "Warning: Possible
                        // variable encountered in reading symbolic constant.\n
                        // If a constant was intended, surround constant with
                        // vertical bars.");
                        // TODO: should this be appended to previous XML
                        // message, or should it be a separate message?
                        print_location_of_most_recent_lexeme();
                    }
                }
                else
                {
                    if (lexeme.at(lexeme.length() - 1) == '>')
                    {
                        if (lexeme.at(lexeme.length() - 2) == '>')
                        {
                            printer.print(
                               "Warning: Possible disjunctive encountered in reading symbolic constant\n" +
                               " If a disjunctive was intended, add a space before >>\n" +
                               " If a constant was intended, surround constant with vertical bars\n");
                            
                            // TODO
                            // xml_generate_warning(thisAgent, "Warning:
                            // Possible disjunctive encountered in reading
                            // symbolic constant.\n If a disjunctive was
                            // intended, add a space before &gt;&gt;\n If a
                            // constant was intended, surround constant with
                            // vertical bars.");
                            // TODO: should this be appended to previous XML
                            // message, or should it be a separate message?
                            print_location_of_most_recent_lexeme();

                        }
                        else
                        {
                            printer.print(
                               "Warning: Possible variable encountered in reading symbolic constant\n" +
                               " If a constant was intended, surround constant with vertical bars\n");
                            
                            // TODO
                            // xml_generate_warning(thisAgent, "Warning:
                            // Possible variable encountered in reading symbolic
                            // constant.\n If a constant was intended, surround
                            // constant with vertical bars.");
                            // //TODO: should this be appended to previous XML
                            // message, or should it be a separate message?
                            print_location_of_most_recent_lexeme();

                            // TODO: generate tagged output in
                            // print_location_of_most_recent_lexeme
                        }
                    }
                }
            }
            return true;
        }

        lexeme.type = LexemeType.QUOTED_STRING;
        return true;
    }

    /**
     * This is the main routine called from outside the lexer. It reads past any
     * whitespace, then calls some lex_xxx routine (using the lexer_routines[]
     * table) based on the first character of the lexeme.
     * 
     * <p>lexer.cpp:816:get_lexeme
     * 
     * @throws IOException
     */
    public void getNextLexeme() throws IOException
    {
        lexeme.string = "";

        consumeWhitespaceAndComments();
        
        // no more whitespace, so go get the actual lexeme
        record_position_of_start_of_lexeme();
        if (current_char != EOF_AS_CHAR)
        {
            lexer_routines[current_char].lex(this);
        }
        else
        {
            lex_eof.lex(this);
        }
    }

    private void consumeWhitespaceAndComments() throws IOException
    {
        while (true)
        {
            if (current_char == EOF_AS_CHAR)
                break;
            
            if (Character.isWhitespace(current_char))
            {
                get_next_char();
                continue;
            }

            // #ifdef USE_TCL
            if (current_char == ';')
            {
                /* --- skip the semi-colon, forces newline in TCL --- */
                get_next_char(); /* consume it */
                continue;
            }
            if (current_char == '#')
            {
                // read from hash to end-of-line
                while ((current_char != '\n') && (current_char != EOF_AS_CHAR))
                {
                    get_next_char();
                }
                
                if (current_char != EOF_AS_CHAR)
                {
                    get_next_char();
                }
                continue;
            }
            break; /* if no whitespace or comments found, break out of the loop */
        }
    }

    /**
     * Initialize the lexer routine table
     * <p>lexer.cpp::init_lexer
     */
    private void init_lexer()
    {
        // setup lexer_routines array
        for (int i = 0; i < lexer_routines.length; i++)
        {
            switch (i)
            {
            case '@': lexer_routines[i] = new BasicLexerRoutine(LexemeType.AT);  break;
            case '(': lexer_routines[i] = lex_lparen; break;
            case ')': lexer_routines[i] = lex_rparen; break;
            case '+': lexer_routines[i] = lex_plus;   break;
            case '-': lexer_routines[i] = lex_minus;  break;
            case '~': lexer_routines[i] = new BasicLexerRoutine(LexemeType.TILDE); break;
            case '^': lexer_routines[i] = new BasicLexerRoutine(LexemeType.UP_ARROW); break;
            case '{': lexer_routines[i] = new BasicLexerRoutine(LexemeType.L_BRACE);  break;
            case '}': lexer_routines[i] = new BasicLexerRoutine(LexemeType.R_BRACE); break;
            case '!': lexer_routines[i] = new BasicLexerRoutine(LexemeType.EXCLAMATION_POINT); break;
            case '>': lexer_routines[i] = lex_greater; break;
            case '<': lexer_routines[i] = lex_less;    break;
            case '=': lexer_routines[i] = lex_equal;   break;
            case '&': lexer_routines[i] = lex_ampersand; break;
            case '|': lexer_routines[i] = lex_vbar;     break;
            case ',': lexer_routines[i] = new BasicLexerRoutine(LexemeType.COMMA); break;
            case '.': lexer_routines[i] = lex_period;   break;
            case '"': lexer_routines[i] = lex_quote;    break;
            default:
                if (Character.isDigit((char) i))
                {
                    lexer_routines[i] = lex_digit;
                }
                else if (isConstituentChar((char) i))
                {
                    lexer_routines[i] = lex_constituent_string;
                }
                else
                {
                    lexer_routines[i] = lex_unknown;
                }
            }
        }
    }

    /*
     * ======================================================================
     * Print location of most recent lexeme
     * 
     * This routine is used to print an indication of where a parser or
     * interface command error occurred. It tries to print out the current
     * source line with a pointer to where the error was detected. If the
     * current source line is no longer available, it just prints out the line
     * number instead.
     * 
     * BUGBUG: if the input line contains any tabs, the pointer comes out in the
     * wrong place.
     * ======================================================================
     */

    private void print_location_of_most_recent_lexeme()
    {
        // TODO
//         int i;
//        
//         if (line_of_start_of_last_lexeme == current_line) {
//         // error occurred on current line, so print out the line
//         if (! reading_from_top_level(thisAgent)) {
//         print (thisAgent, "File %s, line %lu:\n",
//         thisAgent->current_file->filename,
//         thisAgent->current_file->current_line);
//         /* respond_to_load_errors (); AGR 527a */
//         }
//         if(thisAgent->current_file->buffer[strlen(thisAgent->current_file->buffer)-1]=='\n')
//         print_string (thisAgent, thisAgent->current_file->buffer);
//         else
//         print (thisAgent, "%s\n",thisAgent->current_file->buffer);
//         
//         for (i=0; i<thisAgent->current_file->column_of_start_of_last_lexeme;
//         i++)
//         print_string (thisAgent, "-");
//         print_string (thisAgent, "^\n");
//        
//         if (! reading_from_top_level(thisAgent)) {
//         //respond_to_load_errors (thisAgent); /* AGR 527a */
//         if (thisAgent->load_errors_quit)
//         thisAgent->current_char = EOF_AS_CHAR;
//         }
//        
//         /* AGR 527a The respond_to_load_errors call came too early (above),
//         and the "continue" prompt appeared before the offending line was
//         printed
//         out, so the respond_to_load_errors call was moved here.
//         AGR 26-Apr-94 */
//        
//         } else {
//         /* --- error occurred on a previous line, so just give the position
//         --- */
//         print (thisAgent, "File %s, line %lu, column %lu.\n",
//         thisAgent->current_file->filename,
//         thisAgent->current_file->line_of_start_of_last_lexeme,
//         thisAgent->current_file->column_of_start_of_last_lexeme + 1);
//         if (! reading_from_top_level(thisAgent)) {
//         //respond_to_load_errors (thisAgent);
//         if (thisAgent->load_errors_quit)
//         thisAgent->current_char = EOF_AS_CHAR;
//         }
//         }
    }

    /*
     * ======================================================================
     * Set lexer allow ids
     * 
     * This routine should be called to tell the lexer whether to allow
     * identifiers to be read. If FALSE, things that look like identifiers will
     * be returned as SYM_CONSTANT_LEXEME's instead.
     * ======================================================================
     */

    public void setAllowIds(boolean allow_identifiers)
    {
        this.allow_ids = allow_identifiers;
    }
    
    /**
     * This is a utility routine which figures out what kind(s) of symbol a
     * given string could represent. At entry: s, length_of_s represent the
     * string. At exit: possible_xxx is set to TRUE/FALSE to indicate whether
     * the given string could represent that kind of symbol; rereadable is set
     * to TRUE indicating whether the lexer would read the given string as a
     * symbol with exactly the same name (as opposed to treating it as a special
     * lexeme like "+", changing upper to lower case, etc.
     * 
     * <p>lexer.cpp:1225:determine_possible_symbol_types_for_string
     */
    public static PossibleSymbolTypes determine_possible_symbol_types_for_string(String s)
    {

        PossibleSymbolTypes p = new PossibleSymbolTypes();

        if (s.length() == 0)
        {
            return p;
        }

        /* --- check if it's an integer or floating point number --- */
        if (Lexer.number_starters[s.charAt(0)])
        {
            try
            {
                Integer.parseInt(s);
                p.possible_ic = true;
            }
            catch (NumberFormatException e)
            {
            }
            try
            {
                Float.parseFloat(s);
                p.possible_fc = s.indexOf('.') != -1;
            }
            catch (NumberFormatException e)
            {
            }
        }

        /* --- make sure it's entirely constituent characters --- */
        for (int i = 0; i < s.length(); ++i)
        {
            if (!isConstituentChar(s.charAt(i)))
            {
                return p;
            }
        }

        /* --- any string of constituents could be a sym constant --- */
        p.possible_sc = true;

        /* --- check whether it's a variable --- */
        if ((s.charAt(0) == '<') && (s.charAt(s.length() - 1) == '>'))
        {
            p.possible_var = true;
        }
        
        /* --- check for rereadability --- */
        boolean rereadability_questionable = false;
        boolean rereadability_dead = false;
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            if (Character.isLowerCase(ch) || Character.isDigit(ch))
                continue; /* these guys are fine */
            if (Character.isUpperCase(ch))
            {
                rereadability_dead = true;
                break;
            }
            rereadability_questionable = true;
        }
        if (!rereadability_dead)
        {
            if ((!rereadability_questionable) || (s.length() >= LENGTH_OF_LONGEST_SPECIAL_LEXEME)
                    || ((s.length() == 1) && (s.charAt(0) == '*')))
                p.rereadable = true;
        }

        /* --- check if it's an identifier --- */
        if (Character.isLetter(s.charAt(0)))
        {
            /* --- is the rest of the string an integer? --- */
            int i = 1;
            while (i < s.length() && Character.isDigit(s.charAt(i)))
            {
                ++i;
            }
            p.possible_id = i == s.length();
        }
        return p;
    }

}
