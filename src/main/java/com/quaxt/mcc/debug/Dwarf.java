package com.quaxt.mcc.debug;

import com.quaxt.mcc.asm.DebugLineString;
import com.quaxt.mcc.asm.DebugString;
import com.quaxt.mcc.asm.FunctionAsm;
import com.quaxt.mcc.asm.TopLevelAsm;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.quaxt.mcc.Mcc.makeTemporary;
import static com.quaxt.mcc.Mcc.printIndent;

public class Dwarf {
    /* Throughout the comments in this class section numbers and table numbers
     * refer to DWARF Version 5 Debugging Format Standard
     * https://dwarfstd.org/doc/DWARF5.pdf*/

    // 7.5.1 Unit Headers
    static final byte DW_UT_compile = 0x01;

    //Table 7.3: Tag encodings
    static final int DW_TAG_array_type = 0x01;
    static final int DW_TAG_class_type = 0x02;
    static final int DW_TAG_entry_point = 0x03;
    static final int DW_TAG_enumeration_type = 0x04;
    static final int DW_TAG_formal_parameter = 0x05;
    static final int DW_TAG_imported_declaration = 0x08;
    static final int DW_TAG_label = 0x0a;
    static final int DW_TAG_lexical_block = 0x0b;
    static final int DW_TAG_member = 0x0d;
    static final int DW_TAG_pointer_type = 0x0f;
    static final int DW_TAG_reference_type = 0x10;
    static final int DW_TAG_compile_unit = 0x11;
    static final int DW_TAG_string_type = 0x12;
    static final int DW_TAG_structure_type = 0x13;
    static final int DW_TAG_subroutine_type = 0x15;
    static final int DW_TAG_typedef = 0x16;
    static final int DW_TAG_union_type = 0x17;
    static final int DW_TAG_unspecified_parameters = 0x18;
    static final int DW_TAG_variant = 0x19;
    static final int DW_TAG_common_block = 0x1a;
    static final int DW_TAG_common_inclusion = 0x1b;
    static final int DW_TAG_inheritance = 0x1c;
    static final int DW_TAG_inlined_subroutine = 0x1d;
    static final int DW_TAG_module = 0x1e;
    static final int DW_TAG_ptr_to_member_type = 0x1f;
    static final int DW_TAG_set_type = 0x20;
    static final int DW_TAG_subrange_type = 0x21;
    static final int DW_TAG_with_stmt = 0x22;
    static final int DW_TAG_access_declaration = 0x23;
    static final int DW_TAG_base_type = 0x24;
    static final int DW_TAG_catch_block = 0x25;
    static final int DW_TAG_const_type = 0x26;
    static final int DW_TAG_constant = 0x27;
    static final int DW_TAG_enumerator = 0x28;
    static final int DW_TAG_file_type = 0x29;
    static final int DW_TAG_friend = 0x2a;
    static final int DW_TAG_namelist = 0x2b;
    static final int DW_TAG_namelist_item = 0x2c;
    static final int DW_TAG_packed_type = 0x2d;
    static final int DW_TAG_subprogram = 0x2e;
    static final int DW_TAG_template_type_parameter = 0x2f;
    static final int DW_TAG_template_value_parameter = 0x30;
    static final int DW_TAG_thrown_type = 0x31;
    static final int DW_TAG_try_block = 0x32;
    static final int DW_TAG_variant_part = 0x33;
    static final int DW_TAG_variable = 0x34;
    static final int DW_TAG_volatile_type = 0x35;
    static final int DW_TAG_dwarf_procedure = 0x36;
    static final int DW_TAG_restrict_type = 0x37;
    static final int DW_TAG_interface_type = 0x38;
    static final int DW_TAG_namespace = 0x39;
    static final int DW_TAG_imported_module = 0x3a;
    static final int DW_TAG_unspecified_type = 0x3b;
    static final int DW_TAG_partial_unit = 0x3c;
    static final int DW_TAG_imported_unit = 0x3d;
    static final int DW_TAG_condition = 0x3f;
    static final int DW_TAG_shared_type = 0x40;
    static final int DW_TAG_type_unit = 0x41;
    static final int DW_TAG_rvalue_reference_type = 0x42;
    static final int DW_TAG_template_alias = 0x43;
    static final int DW_TAG_coarray_type  = 0x44;
    static final int DW_TAG_generic_subrange  = 0x45;
    static final int DW_TAG_dynamic_type  = 0x46;
    static final int DW_TAG_atomic_type  = 0x47;
    static final int DW_TAG_call_site  = 0x48;
    static final int DW_TAG_call_site_parameter  = 0x49;
    static final int DW_TAG_skeleton_unit  = 0x4a;
    static final int DW_TAG_immutable_type  = 0x4b;
    static final int DW_TAG_lo_user = 0x4080;
    static final int DW_TAG_hi_user = 0xffff;

    // Table 7.4: Child determination encodings
    static final byte DW_CHILDREN_no = 0x00;
    static final byte DW_CHILDREN_yes = 0x01;


    // 7.5.4 Attribute Encodings
    static final int DW_AT_sibling = 0x01;
    static final int DW_AT_location = 0x02;
    static final int DW_AT_name = 0x03;
    static final int DW_AT_ordering = 0x09;
    static final int DW_AT_byte_size = 0x0b;
    static final int DW_AT_bit_size = 0x0d;
    static final int DW_AT_stmt_list = 0x10;
    static final int DW_AT_low_pc = 0x11;
    static final int DW_AT_high_pc = 0x12;
    static final int DW_AT_language = 0x13;
    static final int DW_AT_discr = 0x15;
    static final int DW_AT_discr_value = 0x16;
    static final int DW_AT_visibility = 0x17;
    static final int DW_AT_import = 0x18;
    static final int DW_AT_string_length = 0x19;
    static final int DW_AT_common_reference = 0x1a;
    static final int DW_AT_comp_dir = 0x1b;
    static final int DW_AT_const_value = 0x1c;
    static final int DW_AT_containing_type = 0x1d;
    static final int DW_AT_default_value = 0x1e;
    static final int DW_AT_inline = 0x20;
    static final int DW_AT_is_optional = 0x21;
    static final int DW_AT_lower_bound = 0x22;
    static final int DW_AT_producer = 0x25;
    static final int DW_AT_prototyped = 0x27;
    static final int DW_AT_return_addr = 0x2a;
    static final int DW_AT_start_scope = 0x2c;
    static final int DW_AT_bit_stride = 0x2e;
    static final int DW_AT_upper_bound = 0x2f;
    static final int DW_AT_abstract_origin = 0x31;
    static final int DW_AT_accessibility = 0x32;
    static final int DW_AT_address_class = 0x33;
    static final int DW_AT_artificial = 0x34;
    static final int DW_AT_base_types = 0x35;
    static final int DW_AT_calling_convention = 0x36;
    static final int DW_AT_count = 0x37;
    static final int DW_AT_data_member_location = 0x38;
    static final int DW_AT_decl_column = 0x39;
    static final int DW_AT_decl_file = 0x3a;
    static final int DW_AT_decl_line = 0x3b;
    static final int DW_AT_declaration = 0x3c;
    static final int DW_AT_discr_list = 0x3d;
    static final int DW_AT_encoding = 0x3e;
    static final int DW_AT_external = 0x3f;
    static final int DW_AT_frame_base = 0x40;
    static final int DW_AT_friend = 0x41;
    static final int DW_AT_identifier_case = 0x42;
    static final int DW_AT_namelist_item = 0x44;
    static final int DW_AT_priority = 0x45;
    static final int DW_AT_segment = 0x46;
    static final int DW_AT_specification = 0x47;
    static final int DW_AT_static_link = 0x48;
    static final int DW_AT_type = 0x49;
    static final int DW_AT_use_location = 0x4a;
    static final int DW_AT_variable_parameter = 0x4b;
    static final int DW_AT_virtuality = 0x4c;
    static final int DW_AT_vtable_elem_location = 0x4d;
    static final int DW_AT_allocated = 0x4e;
    static final int DW_AT_associated = 0x4f;
    static final int DW_AT_data_location = 0x50;
    static final int DW_AT_byte_stride = 0x51;
    static final int DW_AT_entry_pc = 0x52;
    static final int DW_AT_use_UTF8 = 0x53;
    static final int DW_AT_extension = 0x54;
    static final int DW_AT_ranges = 0x55;
    static final int DW_AT_trampoline = 0x56;
    static final int DW_AT_call_column = 0x57;
    static final int DW_AT_call_file = 0x58;
    static final int DW_AT_call_line = 0x59;
    static final int DW_AT_description = 0x5a;
    static final int DW_AT_binary_scale = 0x5b;
    static final int DW_AT_decimal_scale = 0x5c;
    static final int DW_AT_small = 0x5d;
    static final int DW_AT_decimal_sign = 0x5e;
    static final int DW_AT_digit_count = 0x5f;
    static final int DW_AT_picture_string = 0x60;
    static final int DW_AT_mutable = 0x61;
    static final int DW_AT_threads_scaled = 0x62;
    static final int DW_AT_explicit = 0x63;
    static final int DW_AT_object_pointer = 0x64;
    static final int DW_AT_endianity = 0x65;
    static final int DW_AT_elemental = 0x66;
    static final int DW_AT_pure = 0x67;
    static final int DW_AT_recursive = 0x68;
    static final int DW_AT_signature = 0x69;
    static final int DW_AT_main_subprogram = 0x6a;
    static final int DW_AT_data_bit_offset = 0x6b;
    static final int DW_AT_const_expr = 0x6c;
    static final int DW_AT_enum_class = 0x6d;
    static final int DW_AT_linkage_name = 0x6e;
    static final int DW_AT_string_length_bit_size = 0x6f;
    static final int DW_AT_string_length_byte_size = 0x70;
    static final int DW_AT_rank = 0x71;
    static final int DW_AT_str_offsets_base = 0x72;
    static final int DW_AT_addr_base = 0x73;
    static final int DW_AT_rnglists_base = 0x74;
    static final int DW_AT_dwo_name = 0x76;
    static final int DW_AT_reference = 0x77;
    static final int DW_AT_rvalue_reference = 0x78;
    static final int DW_AT_macros = 0x79;
    static final int DW_AT_call_all_calls = 0x7a;
    static final int DW_AT_call_all_source_calls = 0x7b;
    static final int DW_AT_call_all_tail_calls = 0x7c;
    static final int DW_AT_call_return_pc = 0x7d;
    static final int DW_AT_call_value = 0x7e;
    static final int DW_AT_call_origin = 0x7f;
    static final int DW_AT_call_parameter = 0x80;
    static final int DW_AT_call_pc = 0x81;
    static final int DW_AT_call_tail_call = 0x82;
    static final int DW_AT_call_target = 0x83;
    static final int DW_AT_call_target_clobbered = 0x84;
    static final int DW_AT_call_data_location = 0x85;
    static final int DW_AT_call_data_value = 0x86;
    static final int DW_AT_noreturn = 0x87;
    static final int DW_AT_alignment = 0x88;
    static final int DW_AT_export_symbols = 0x89;
    static final int DW_AT_deleted = 0x8a;
    static final int DW_AT_defaulted = 0x8b;
    static final int DW_AT_loclists_base = 0x8c;
    static final int DW_AT_lo_user = 0x2000;
    static final int DW_AT_hi_user = 0x3fff;

    // https://dwarfstd.org/languages-v6.html
    static final int DW_AT_language_name = 0x90;
    static final int DW_AT_language_version = 0x91;


    // 7.5.6 Form Encodings
    static final int DW_FORM_addr = 0x01;
    static final int DW_FORM_block2 = 0x03;
    static final int DW_FORM_block4 = 0x04;
    static final int DW_FORM_data2 = 0x05;
    static final int DW_FORM_data4 = 0x06;
    static final int DW_FORM_data8 = 0x07;
    static final int DW_FORM_string = 0x08;
    static final int DW_FORM_block = 0x09;
    static final int DW_FORM_block1 = 0x0a;
    static final int DW_FORM_data1 = 0x0b;
    static final int DW_FORM_flag = 0x0c;
    static final int DW_FORM_sdata = 0x0d;
    static final int DW_FORM_strp = 0x0e;
    static final int DW_FORM_udata = 0x0f;
    static final int DW_FORM_ref_addr = 0x10;
    static final int DW_FORM_ref1 = 0x11;
    static final int DW_FORM_ref2 = 0x12;
    static final int DW_FORM_ref4 = 0x13;
    static final int DW_FORM_ref8 = 0x14;
    static final int DW_FORM_ref_udata = 0x15;
    static final int DW_FORM_indirect = 0x16;
    static final int DW_FORM_sec_offset = 0x17;
    static final int DW_FORM_exprloc = 0x18;
    static final int DW_FORM_flag_present = 0x19;
    static final int DW_FORM_strx = 0x1a;
    static final int DW_FORM_addrx = 0x1b;
    static final int DW_FORM_ref_sup4 = 0x1c;
    static final int DW_FORM_strp_sup = 0x1d;
    static final int DW_FORM_data16 = 0x1e;
    static final int DW_FORM_line_strp = 0x1f;
    static final int DW_FORM_ref_sig8 = 0x20;
    static final int DW_FORM_implicit_const = 0x21;
    static final int DW_FORM_loclistx = 0x22;
    static final int DW_FORM_rnglistx = 0x23;
    static final int DW_FORM_ref_sup8 = 0x24;
    static final int DW_FORM_strx1 = 0x25;
    static final int DW_FORM_strx2 = 0x26;
    static final int DW_FORM_strx3 = 0x27;
    static final int DW_FORM_strx4 = 0x28;
    static final int DW_FORM_addrx1 = 0x29;
    static final int DW_FORM_addrx2 = 0x2a;
    static final int DW_FORM_addrx3 = 0x2b;
    static final int DW_FORM_addrx4 = 0x2c;



    // https://dwarfstd.org/languages.html
    static final byte DW_LANG_C11 = 0x1d;
    static final byte DW_LNAME_C = 0x03;	;

    public static void emitDebugInfo(PrintWriter out,
                                     List<TopLevelAsm> topLevelAsms,
                                     Path srcFile,
                                     String textStart,
                                     String textEnd) {
        printIndent(out, ".section\t.debug_info,\"\",@progbits\n");
        String startLabel = makeTemporary(".Lstart.");
        String endLabel = makeTemporary(".Lend.");
        String debugLineLabel = makeTemporary(".LabbrevLabel.");
        String abbrevLabel = makeTemporary(".LabbrevLabel.");
        // The compilation unit header is defined in 7.5.1.1 Full and Partial
        // Compilation Unit Headers
        printIndent(out, ".long\t" + endLabel + "-" + startLabel); //length
        out.println(startLabel + ":");
        printIndent(out, ".value\t5"); // DWARF version
        printByte(out, DW_UT_compile);// Unit Type:     DW_UT_compile
        printByte(out, (byte) 8);// address_size
        printIndent(out, ".long\t" + abbrevLabel);// debug_abbrev_offset

        // ---- Compile Unit DIE ----
        uleb128(out, 1); // abbreviation code DW_TAG_compile_unit

        // DW_AT_producer
        String producer = makeTemporary(".Lproducer.");
        topLevelAsms.add(new DebugString(producer, "mcc"));
        printIndent(out, ".long\t" + producer);
        printByte(out, DW_LANG_C11);
        printByte(out, DW_LNAME_C);
        printInt(out, 202311);


        // DW_AT_name

        String name = makeTemporary(".Lname.");
        topLevelAsms.add(new DebugLineString(name, srcFile.getFileName().toString()));
        printIndent(out, ".long\t" + name);

        // DW_AT_comp_dir
        var compDirPath = srcFile.getParent();
        if (compDirPath != null) {
            String compDir = makeTemporary(".LcompDir.");
            topLevelAsms.add(new DebugLineString(compDir, compDirPath.toString()));
            printIndent(out, ".long\t" + compDir);

        }
        // DW_AT_low_pc
        printQuad(out,  textStart);

        // DW_AT_high_pc
        printQuad(out, textEnd + "-" + textStart);
        // DW_AT_stmt_list
        printInt(out, debugLineLabel);
        // now types
        // now subprograms
        List<FunctionAsm> functions = new ArrayList<>();
        for (TopLevelAsm topLevelAsm : topLevelAsms) {
            if (topLevelAsm instanceof FunctionAsm fun) {
                functions.add(fun);
            }
        }

        for (FunctionAsm fun : functions) {
                uleb128(out, 2); // abbreviation code DW_TAG_subprogram

                String funName = makeTemporary(".LfunName.");
                topLevelAsms.add(new DebugString(funName, fun.name));
                printIndent(out, ".long\t" + funName);

        }
        // no more children of DW_TAG_compile_unit
        printByte(out, (byte)0);


        out.println(endLabel + ":");

        printIndent(out, ".section\t.debug_line,\"\",@progbits");
        out.println(debugLineLabel+":");



        // See 7.5.3 Abbreviations Table
        printIndent(out, ".section\t.debug_abbrev,\"\",@progbits");
        out.println(abbrevLabel+":");
        uleb128(out, 1); // abbreviation code
        uleb128(out, DW_TAG_compile_unit);
        printByte(out, DW_CHILDREN_yes);

        uleb128s(out, new int[]{DW_AT_producer, DW_FORM_strp, DW_AT_language,
                DW_FORM_data1, DW_AT_language_name, DW_FORM_data1,
                DW_AT_language_version, DW_FORM_data4, DW_AT_name,
                DW_FORM_line_strp, DW_AT_comp_dir, DW_FORM_line_strp,
                DW_AT_low_pc, DW_FORM_addr, DW_AT_high_pc, DW_FORM_data8,
                DW_AT_stmt_list, DW_FORM_sec_offset});

        // end of DW_TAG_compile_unit
        printByte(out, (byte) 0);
        printByte(out, (byte) 0);

        uleb128(out, 2); // abbreviation code
        uleb128(out, DW_TAG_subprogram);
        printByte(out, DW_CHILDREN_no); // MR-TODO change to yes

        uleb128s(out, new int[]{
                DW_AT_external, DW_FORM_flag_present,
                DW_AT_name, DW_FORM_strp,

//                DW_AT_decl_file, DW_FORM_data1,
//                DW_AT_decl_line, DW_FORM_data2, DW_AT_decl_column,
//                DW_FORM_data1, DW_AT_prototyped, DW_FORM_flag_present,
//                DW_AT_type, DW_FORM_ref4, DW_AT_declaration,
//                DW_FORM_flag_present, DW_AT_sibling, DW_FORM_ref4
        });
        // end of D,W_TAG_subprogram;
        printByte(out, (byte) 0);
        printByte(out, (byte) 0);

        //end of debug abbrev
        printByte(out, (byte) 0);

    }

    private static void printInt(PrintWriter out, String s) {
        printIndent(out, ".int\t" + s);
    }

    private static void printShort(PrintWriter out, short s) {
        printIndent(out, ".value\t0x" + Integer.toHexString(s));
    }

    private static void printByte(PrintWriter out, byte b) {
        printIndent(out, ".byte\t0x" + Integer.toHexString(b));
    }
    private static void printQuad(PrintWriter out, long q) {
        printIndent(out, ".quad\t0x" + Long.toHexString(q));
    }
    private static void printQuad(PrintWriter out, String s) {
        printIndent(out, ".quad\t" + s);
    }
    private static void printInt(PrintWriter out, int i) {
        printIndent(out, ".long\t0x" + Integer.toHexString(i));
    }
    private static void uleb128(PrintWriter out, int i) {
        printIndent(out, ".uleb128\t0x" + Integer.toHexString(i));
    }

    private static void uleb128s(PrintWriter out, int[] a) {
        for (int i : a)
            printIndent(out, ".uleb128\t0x" + Integer.toHexString(i));
    }

}
