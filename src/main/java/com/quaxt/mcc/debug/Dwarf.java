package com.quaxt.mcc.debug;

import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.StructDef;
import com.quaxt.mcc.SymbolTableEntry;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.semantic.*;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;

import static com.quaxt.mcc.Mcc.makeTemporary;
import static com.quaxt.mcc.Mcc.printIndent;
import static com.quaxt.mcc.semantic.SemanticAnalysis.isComplete;

public class Dwarf {
    private static final int ROOT_DEBUG_SCOPE_ID = 0;

    /* Throughout the comments in this class section numbers and table numbers
     * refer to DWARF Version 5 Debugging Format Standard
     * https://dwarfstd.org/doc/DWARF5.pdf
     * */

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

    record Die(int tag, boolean hasChildren, int[] attribFormPairs){
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Die die)) return false;
            return tag == die.tag && hasChildren == die.hasChildren && Objects.deepEquals(attribFormPairs, die.attribFormPairs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tag, hasChildren, Arrays.hashCode(attribFormPairs));
        }
    };


    // https://dwarfstd.org/languages.html
    static final byte DW_LANG_C11 = 0x1d;
    static final byte DW_LNAME_C = 0x03;	;

// DWARF 5 7.8 Base type atribute encodings
    static final byte DW_ATE_address = 0x01;
    static final byte DW_ATE_boolean = 0x02;
    static final byte DW_ATE_complex_float = 0x03;
    static final byte DW_ATE_float = 0x04;
    static final byte DW_ATE_signed = 0x05;
    static final byte DW_ATE_signed_char = 0x06;
    static final byte DW_ATE_unsigned = 0x07;
    static final byte DW_ATE_unsigned_char = 0x08;
    static final byte DW_ATE_imaginary_float = 0x09;
    static final byte DW_ATE_packed_decimal = 0x0a;
    static final byte DW_ATE_numeric_string = 0x0b;
    static final byte DW_ATE_edited = 0x0c;
    static final byte DW_ATE_signed_fixed = 0x0d;
    static final byte DW_ATE_unsigned_fixed = 0x0e;
    static final byte DW_ATE_decimal_float = 0x0f;
    static final byte DW_ATE_UTF = 0x10;
    static final byte DW_ATE_UCS = 0x11;
    static final byte DW_ATE_ASCII = 0x12;
    static final byte DW_ATE_lo_user = (byte)0x80;
    static final byte DW_ATE_hi_user = (byte)0xff;

    // Table 7.9: DWARF operation encodings
    static final byte DW_OP_reg0 = 0x50;
    static final byte DW_OP_reg1 = 0x51;
    static final byte DW_OP_reg7 = 0x57;
    static final byte DW_OP_reg31 = 0x6f;
    static final byte DW_OP_breg0 = 0x70;
    static final byte DW_OP_breg1 = 0x71;
    static final byte DW_OP_breg31 = (byte) 0x8f;
    static final byte DW_OP_regx = (byte) 0x90;
    static final byte DW_OP_fbreg = (byte) 0x91;
    static final byte DW_OP_bregx = (byte) 0x92;
    static final byte DW_OP_piece = (byte) 0x93;
    static final byte DW_OP_deref_size = (byte) 0x94;
    static final byte DW_OP_xderef_size = (byte) 0x95;
    static final byte DW_OP_nop = (byte) 0x96;
    static final byte DW_OP_push_object_address = (byte) 0x97;
    static final byte DW_OP_call2 = (byte) 0x98;
    static final byte DW_OP_call4 = (byte) 0x99;
    static final byte DW_OP_call_ref = (byte) 0x9a;
    static final byte DW_OP_form_tls_address = (byte) 0x9b;
    static final byte DW_OP_call_frame_cfa = (byte) 0x9c;
    static final byte DW_OP_bit_piece = (byte) 0x9d;
    static final byte DW_OP_implicit_value = (byte) 0x9e;
    static final byte DW_OP_stack_value = (byte) 0x9f;
    static final byte DW_OP_implicit_pointer = (byte) 0xa0;
    static final byte DW_OP_addrx = (byte) 0xa1;
    static final byte DW_OP_constx = (byte) 0xa2;

    // 7.28.2 Location List Entry Encodings
    static final byte DW_LLE_end_of_list = 0x00;
    static final byte DW_LLE_start_end = 0x07;

    public static void emitDebugInfo(PrintWriter out,
                                     List<TopLevelAsm> topLevelAsms,
                                     Path srcFile,
                                     String textStart,
                                     String textEnd,
                                     List<Position> positions) {
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
        printByte(out, (byte) 8); // address_size
        printIndent(out, ".long\t" + abbrevLabel);// debug_abbrev_offset
        LinkedHashMap<Die, Integer> dieMap = new LinkedHashMap<>();
        Die compilationUnitDie = new Die(DW_TAG_compile_unit, true, new int[]{
                DW_AT_producer, DW_FORM_strp, DW_AT_language,
                DW_FORM_data1, DW_AT_language_name, DW_FORM_data1,
                DW_AT_language_version, DW_FORM_data4, DW_AT_name,
                DW_FORM_line_strp, DW_AT_comp_dir, DW_FORM_line_strp,
                DW_AT_low_pc, DW_FORM_addr, DW_AT_high_pc, DW_FORM_data8,
                DW_AT_stmt_list, DW_FORM_sec_offset});
        int compileUnitAbbrevCode = abbrevNumber(dieMap, compilationUnitDie);
        // ---- Compile Unit DIE ----
        uleb128(out, compileUnitAbbrevCode); // abbreviation code DW_TAG_compile_unit

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
        List<FunctionIr> functions = new ArrayList<>();
        for (TopLevelAsm topLevelAsm : topLevelAsms) {
            if (topLevelAsm instanceof FunctionIr fun) {
                functions.add(fun);
            }
        }


        LinkedHashMap<Type, Integer> typeMap = makeTypeTable(out, topLevelAsms, dieMap);
        int[] voidExprFrameBaseAttribs = new int[]{
                DW_AT_external, DW_FORM_flag_present,
                DW_AT_name, DW_FORM_strp,
                DW_AT_decl_file, DW_FORM_data1,
                DW_AT_decl_line, DW_FORM_data2,
                DW_AT_prototyped, DW_FORM_flag_present,
                DW_AT_low_pc, DW_FORM_addr,
                DW_AT_high_pc, DW_FORM_data8,
                DW_AT_frame_base, DW_FORM_exprloc
//                DW_AT_type, DW_FORM_ref4,
//                DW_AT_declaration, DW_FORM_flag_present,
//                DW_AT_sibling, DW_FORM_ref4
        };
        int[] nonvoidExprFrameBaseAttribs = new int[]{
                DW_AT_external, DW_FORM_flag_present,
                DW_AT_name, DW_FORM_strp,
                DW_AT_decl_file, DW_FORM_data1,
                DW_AT_decl_line, DW_FORM_data2,
                DW_AT_prototyped, DW_FORM_flag_present,
                DW_AT_type, DW_FORM_ref4,
                DW_AT_low_pc, DW_FORM_addr,
                DW_AT_high_pc, DW_FORM_data8,
                DW_AT_frame_base, DW_FORM_exprloc
//                DW_AT_declaration, DW_FORM_flag_present,
//                DW_AT_sibling, DW_FORM_ref4
        };
        int[] voidLocListFrameBaseAttribs = new int[]{
                DW_AT_external, DW_FORM_flag_present,
                DW_AT_name, DW_FORM_strp,
                DW_AT_decl_file, DW_FORM_data1,
                DW_AT_decl_line, DW_FORM_data2,
                DW_AT_prototyped, DW_FORM_flag_present,
                DW_AT_low_pc, DW_FORM_addr,
                DW_AT_high_pc, DW_FORM_data8,
                DW_AT_frame_base, DW_FORM_sec_offset
        };
        int[] nonvoidLocListFrameBaseAttribs = new int[]{
                DW_AT_external, DW_FORM_flag_present,
                DW_AT_name, DW_FORM_strp,
                DW_AT_decl_file, DW_FORM_data1,
                DW_AT_decl_line, DW_FORM_data2,
                DW_AT_prototyped, DW_FORM_flag_present,
                DW_AT_type, DW_FORM_ref4,
                DW_AT_low_pc, DW_FORM_addr,
                DW_AT_high_pc, DW_FORM_data8,
                DW_AT_frame_base, DW_FORM_sec_offset
        };
        for (FunctionIr fun : functions) {
            Type returnType = fun.returnType();
            boolean hasChildren = hasEmittableDebugChildren(fun);
            boolean usesFrameBaseLocList = fun.frameBaseLocListLabel != null &&
                    !fun.frameBaseRanges.isEmpty();
            int[] subprogramAttribs =
                    returnType == Primitive.VOID ?
                            (usesFrameBaseLocList ?
                                    voidLocListFrameBaseAttribs :
                                    voidExprFrameBaseAttribs) :
                            (usesFrameBaseLocList ?
                                    nonvoidLocListFrameBaseAttribs :
                                    nonvoidExprFrameBaseAttribs);


            int subProgramCode = abbrevNumber(dieMap, new Die(DW_TAG_subprogram, hasChildren, subprogramAttribs));
            uleb128(out, subProgramCode); // abbreviation code DW_TAG_subprogram
            String funName = makeTemporary(".LfunName.");
            topLevelAsms.add(new DebugString(funName, fun.name));
            printIndent(out, ".long\t" + funName);
            Position pos = positions.get(fun.pos);
            printByte(out, (byte) pos.file());
            printShort(out, (short) pos.lineNumber());
            if (returnType != Primitive.VOID) {
                printInt(out, typeMap.get(returnType));
            }

            // DW_AT_low_pc
            printQuad(out,  fun.name);

            // DW_AT_high_pc
            printQuad(out,".L"+fun.name+".end"  + "-" + fun.name);

            if (usesFrameBaseLocList) {
                printInt(out, fun.frameBaseLocListLabel);
            } else {
                long frameBaseOffset = fun.frameBaseRanges.isEmpty() ? 0 :
                        -fun.frameBaseRanges.getFirst().spDelta();
                emitFrameBaseExpression(out, frameBaseOffset);
            }

            if (hasChildren) {
                emitFunctionDebugChildren(out, fun, topLevelAsms, dieMap,
                        typeMap);
                printByte(out, (byte) 0);
                printByte(out, (byte) 0);
            }

        }


        out.println(endLabel + ":");

        emitDebugLocLists(out, functions);

        printIndent(out, ".section\t.debug_line,\"\",@progbits");
        out.println(debugLineLabel+":");



        // See 7.5.3 Abbreviations Table
        printIndent(out, ".section\t.debug_abbrev,\"\",@progbits");
        out.println(abbrevLabel+":");
        for (var e : dieMap.entrySet()) {
            Die k = e.getKey();
            int abbrevCode = e.getValue();
            uleb128(out, abbrevCode); // abbreviation code
            uleb128(out, k.tag);
            uleb128(out, k.hasChildren ? DW_CHILDREN_yes : DW_CHILDREN_no);
            uleb128s(out, k.attribFormPairs);
            // end of die abbrev
            printByte(out, (byte) 0);
            printByte(out, (byte) 0);

        }

        //end of debug abbrev
        printByte(out, (byte) 0);

    }

    private static boolean hasEmittableDebugChildren(FunctionIr fun) {
        if (fun.debugScopes != null && !fun.debugScopes.isEmpty()) {
            return true;
        }
        if (fun.debugLocals == null) {
            return false;
        }
        for (DebugLocal local : fun.debugLocals) {
            if ((fun.debugRegisterTable != null &&
                    fun.debugRegisterTable.containsKey(local.internalName())) ||
                    (fun.varTable != null &&
                            fun.varTable.containsKey(local.internalName()))) {
                return true;
            }
        }
        return false;
    }

    private static void emitFunctionDebugChildren(PrintWriter out,
                                                  FunctionIr fun,
                                                  List<TopLevelAsm> topLevelAsms,
                                                  LinkedHashMap<Die, Integer> dieMap,
                                                  LinkedHashMap<Type, Integer> typeMap) {
        Die variableDie = new Die(DW_TAG_variable, false,
                new int[]{
                        DW_AT_name,         DW_FORM_strp,
                        DW_AT_type,         DW_FORM_ref4,
                        DW_AT_location, DW_FORM_exprloc});
        Die parameterDie = new Die(DW_TAG_formal_parameter, false,
                new int[]{
                        DW_AT_name,         DW_FORM_strp,
                        DW_AT_type,         DW_FORM_ref4,
                        DW_AT_location, DW_FORM_exprloc});
        Die lexicalBlockDie = new Die(DW_TAG_lexical_block, true,
                new int[]{
                        DW_AT_low_pc, DW_FORM_addr,
                        DW_AT_high_pc, DW_FORM_data8});
        int variableDieAbbrevNumber = abbrevNumber(dieMap, variableDie);
        int parameterDieAbbrevNumber = abbrevNumber(dieMap, parameterDie);
        int lexicalBlockDieAbbrevNumber =
                abbrevNumber(dieMap, lexicalBlockDie);

        Map<Integer, List<DebugLocal>> localsByScope = new HashMap<>();
        if (fun.debugLocals != null) {
            for (DebugLocal local : fun.debugLocals) {
                localsByScope.computeIfAbsent(local.scopeId(),
                        ignored -> new ArrayList<>()).add(local);
            }
        }

        Map<Integer, List<DebugScope>> scopesByParent = new HashMap<>();
        if (fun.debugScopes != null) {
            for (DebugScope scope : fun.debugScopes) {
                scopesByParent.computeIfAbsent(scope.parentId(),
                        ignored -> new ArrayList<>()).add(scope);
            }
        }

        emitScopeDebugChildren(out, fun, ROOT_DEBUG_SCOPE_ID, topLevelAsms,
                typeMap, localsByScope, scopesByParent,
                variableDieAbbrevNumber, parameterDieAbbrevNumber,
                lexicalBlockDieAbbrevNumber);
    }

    private static void emitScopeDebugChildren(PrintWriter out,
                                               FunctionIr fun,
                                               int scopeId,
                                               List<TopLevelAsm> topLevelAsms,
                                               LinkedHashMap<Type, Integer> typeMap,
                                               Map<Integer, List<DebugLocal>> localsByScope,
                                               Map<Integer, List<DebugScope>> scopesByParent,
                                               int variableDieAbbrevNumber,
                                               int parameterDieAbbrevNumber,
                                               int lexicalBlockDieAbbrevNumber) {
        for (DebugLocal local : localsByScope.getOrDefault(scopeId, List.of())) {
            emitLocalDebugInfo(out, fun, local, topLevelAsms, typeMap,
                    variableDieAbbrevNumber, parameterDieAbbrevNumber);
        }

        for (DebugScope scope : scopesByParent.getOrDefault(scopeId,
                List.of())) {
            uleb128(out, lexicalBlockDieAbbrevNumber);
            printQuad(out, scope.startLabel());
            printQuad(out, scope.endLabel() + "-" + scope.startLabel());
            emitScopeDebugChildren(out, fun, scope.id(), topLevelAsms,
                    typeMap, localsByScope, scopesByParent,
                    variableDieAbbrevNumber, parameterDieAbbrevNumber,
                    lexicalBlockDieAbbrevNumber);
            printByte(out, (byte) 0);
            printByte(out, (byte) 0);
        }
    }

    private static void emitLocalDebugInfo(PrintWriter out,
                                           FunctionIr fun,
                                           DebugLocal local,
                                           List<TopLevelAsm> topLevelAsms,
                                           LinkedHashMap<Type, Integer> typeMap,
                                           int variableDieAbbrevNumber,
                                           int parameterDieAbbrevNumber) {
        SymbolTableEntry ste = Mcc.SYMBOL_TABLE.get(local.internalName());
        if (ste == null) {
            return;
        }
        Integer typeDieOffset = typeMap.get(ste.type());
        if (typeDieOffset == null) {
            return;
        }
        Reg reg = fun.debugRegisterTable == null ? null :
                fun.debugRegisterTable.get(local.internalName());
        Long offset = null;
        if (reg == null && fun.varTable != null) {
            offset = fun.varTable.get(local.internalName());
        }
        if (reg == null && offset == null) {
            return;
        }

        uleb128(out, local.parameter() ? parameterDieAbbrevNumber :
                variableDieAbbrevNumber);
        addAndPrintString(out, topLevelAsms, local.displayName());
        printInt(out, typeDieOffset);
        if (reg != null) {
            emitRegisterLocationExpression(out, reg);
            return;
        }
        uleb128(out, 1 + sleb128ByteCount(offset));
        printByte(out, DW_OP_fbreg);
        sleb128(out, offset);
    }

    private static void emitRegisterLocationExpression(PrintWriter out,
                                                       Reg reg) {
        int dwarfNumber = dwarfRegisterNumber(reg);
        if (dwarfNumber <= DW_OP_reg31 - DW_OP_reg0) {
            uleb128(out, 1);
            printByte(out, (byte) (DW_OP_reg0 + dwarfNumber));
            return;
        }
        uleb128(out, 1 + uleb128ByteCount(dwarfNumber));
        printByte(out, DW_OP_regx);
        uleb128(out, dwarfNumber);
    }

    private static int dwarfRegisterNumber(Reg reg) {
        return switch (reg) {
            case IntegerReg integerReg -> integerReg.dwarfNumber;
            case DoubleReg doubleReg -> doubleReg.dwarfNumber;
            case Pseudo _ -> throw new IllegalArgumentException(
                    "Pseudo register has no DWARF register number");
        };
    }

    private static void emitDebugLocLists(PrintWriter out,
                                          List<FunctionIr> functions) {
        boolean any = false;
        for (FunctionIr function : functions) {
            if (function.frameBaseLocListLabel != null &&
                    !function.frameBaseRanges.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }

        printIndent(out, ".section\t.debug_loclists,\"\",@progbits");
        String startLabel = makeTemporary(".LdebugLoclistsStart.");
        String endLabel = makeTemporary(".LdebugLoclistsEnd.");
        printIndent(out, ".long\t" + endLabel + "-" + startLabel);
        out.println(startLabel + ":");
        printIndent(out, ".value\t5");
        printByte(out, (byte) 8); // address size
        printByte(out, (byte) 0); // segment selector size
        printInt(out, 0); // offset entry count

        for (FunctionIr function : functions) {
            if (function.frameBaseLocListLabel == null ||
                    function.frameBaseRanges.isEmpty()) {
                continue;
            }
            out.println(function.frameBaseLocListLabel + ":");
            for (FrameBaseRange range : function.frameBaseRanges) {
                printByte(out, DW_LLE_start_end);
                printQuad(out, range.startLabel());
                printQuad(out, range.endLabel());
                emitFrameBaseExpression(out, -range.spDelta());
            }
            printByte(out, DW_LLE_end_of_list);
        }
        out.println(endLabel + ":");
    }

    private static void emitFrameBaseExpression(PrintWriter out,
                                                long rspOffset) {
        uleb128(out, 1 + sleb128ByteCount(rspOffset));
        printByte(out, (byte) (DW_OP_breg0 + IntegerReg.SP.dwarfNumber));
        sleb128(out, rspOffset);
    }

    private static String displayName(String internalName) {
        int dotIndex = internalName.indexOf('.');
        return dotIndex == -1 ? internalName : internalName.substring(0,
                dotIndex);
    }

    private static LinkedHashMap<Type, Integer> makeTypeTable(PrintWriter out,
                                                              List<TopLevelAsm> topLevelAsms, LinkedHashMap<Die, Integer> dieMap) {
        LinkedHashMap<Type, Integer> typeMap = new LinkedHashMap<>();
        int nextTypeLocation = 0x33;
        // It will be useful when building the type table to know up front for each array type what are all the array sizes
        // So we do a first pass to gather all of them
            HashMap<Type,HashSet<Constant>> arrayTypes = new HashMap<>();
        Set<Type> alreadySeen = new HashSet<>();
        for (SymbolTableEntry e : Mcc.SYMBOL_TABLE.values()) {
            Type t = e.type();
            gatherArrayTypes(alreadySeen, t, arrayTypes);
        }


        // we build the type table twice because in the first run with might have a struct with a member that is a
        // pointer to the struct and at the point of saying what the type of the member is we don't know the ref of the
        // pointer to struct type
        for (SymbolTableEntry e: Mcc.SYMBOL_TABLE.values()) {
            Type t = e.type();
            nextTypeLocation = addType(new PrintWriter(OutputStream.nullOutputStream()), topLevelAsms, dieMap, t, typeMap, nextTypeLocation, arrayTypes, null);
        }

        nextTypeLocation = 0x33;

        // addType uses this param to know what it has already seen
        LinkedHashMap<Type, Integer> temp = new LinkedHashMap<>();
        for (SymbolTableEntry e: Mcc.SYMBOL_TABLE.values()) {
            Type t = e.type();
            nextTypeLocation = addType(out, topLevelAsms, dieMap, t, temp, nextTypeLocation, arrayTypes, typeMap);
        }
        return typeMap;
    }

        private static void gatherArrayTypes(Set<Type> alreadySeen, Type t, HashMap<Type, HashSet<Constant>> arrayTypes) {
            if (alreadySeen.contains(t)) {
                return;
            }
            alreadySeen.add(t);
            switch (t) {
                case Array(Type element, Constant arraySize) -> {
                    arrayTypes.computeIfAbsent(element, k -> new HashSet<>()).add(arraySize);
                    gatherArrayTypes(alreadySeen, element, arrayTypes);
                }
                case Structure(boolean isUnion, String tag, StructDef _) ->{
                    var structDef = Mcc.TYPE_TABLE.get(tag);
                    if (structDef != null) {
                        for (var m : structDef.members()) {
                            Type mt = m.type();
                            gatherArrayTypes(alreadySeen, mt, arrayTypes);
                        }

                    }
                }
                case Pointer(Type referenced) -> {
                    gatherArrayTypes(alreadySeen, referenced, arrayTypes);
                }
                case Primitive _ ->{}
                case FunType(List<Type> params, Type ret, boolean varargs, Exp alignment) ->{
                    gatherArrayTypes(alreadySeen, ret, arrayTypes);
                    for (Type p: params) {
                        gatherArrayTypes(alreadySeen, p, arrayTypes);
                    }
                }

                default -> throw new Todo();
            }

        }

    /**
     * @param nextTypeRef is the offset in bytes from the start of the
     *                    debug_info section where the next type will be written to
     * @param arrayTypes
     * @param finalTypeMap
     */
    private static int addType(PrintWriter out,
                               List<TopLevelAsm> topLevelAsms,
                               LinkedHashMap<Die, Integer> dieMap,
                               Type t,
                               LinkedHashMap<Type, Integer> typeMap, int nextTypeRef, HashMap<Type, HashSet<Constant>> arrayTypes, LinkedHashMap<Type, Integer> finalTypeMap) {
        if (t == Primitive.VOID || typeMap.containsKey(t)) {
            return nextTypeRef;
        }
        // We put a dummy value to prevent stack overflow for case of a recursively defined types (e.g. struct with a member that is a pointer to the same struct)
        typeMap.put(t, 0);
        switch(t) {

            case Array(Type element, Constant arraySize) -> {
                // DWARF forces us to specify the type of the upper bound of arrays
                // It's always unsigned long because that is what size_t is
                nextTypeRef = addType(out, topLevelAsms,
                        dieMap, Primitive.ULONG,
                        typeMap, nextTypeRef, arrayTypes, finalTypeMap);
                int unsignedLongRef = typeMap.get(Primitive.ULONG);

                boolean first=true;
                long max=0;
                for(Constant x:arrayTypes.get(element)) {
                    long v = x.toLong();
                    if (first){
                        max = v;
                    } else {
                        if (v>max) {
                            max=v;
                        }
                        first =false;
                    }
                }
                var dwForm = dataForm(max);
                int dwFormSize = formSize(dwForm);

                nextTypeRef = addType(out, topLevelAsms,
                        dieMap, element,
                        typeMap, nextTypeRef, arrayTypes, finalTypeMap);
                Die arrayTypeDie = new Die(DW_TAG_array_type, true, new int[]{
                        DW_AT_type,         DW_FORM_ref4});

                int typeAbbrevNumber = abbrevNumber(dieMap, arrayTypeDie);

                Die subRangeTypeDie = new Die(DW_TAG_subrange_type, false, new int[]{
                        DW_AT_type,         DW_FORM_ref4,
                        DW_AT_upper_bound,  dwForm
                });

                int subRangeAbbrevNumber = abbrevNumber(dieMap, subRangeTypeDie);
                uleb128(out, typeAbbrevNumber);
                printInt(out, typeMap.get(element));
                nextTypeRef += uleb128ByteCount(typeAbbrevNumber) + 4;
                for (Constant x : arrayTypes.get(element)) {
                    typeMap.put(new Array(element, x), nextTypeRef);
                    uleb128(out, subRangeAbbrevNumber); // abbreviation code
                    printInt(out, unsignedLongRef);
                    printWhatever(out, x.toLong(), dwForm);
                    nextTypeRef += 4 + dwFormSize + uleb128ByteCount(subRangeAbbrevNumber);
                }
                // no more array sizes
                printByte(out, (byte) 0);
                nextTypeRef++;
            }
            case Pointer(Type referenced) -> {
                Die d = referenced==Primitive.VOID?
                        new Die(DW_TAG_pointer_type, false, new int[]{
                        DW_AT_byte_size, DW_FORM_implicit_const, 8}):
                        new Die(DW_TAG_pointer_type, false, new int[]{
                        DW_AT_byte_size, DW_FORM_implicit_const, 8,
                        DW_AT_type, DW_FORM_ref4});
                nextTypeRef = addType(out, topLevelAsms,
                        dieMap, referenced,
                        typeMap, nextTypeRef, arrayTypes, finalTypeMap);
                int typeAbbrevNumber = abbrevNumber(dieMap, d);
                typeMap.put(t, nextTypeRef);
                uleb128(out, typeAbbrevNumber);
                if (referenced != Primitive.VOID) printInt(out, typeMap.get(referenced));
                nextTypeRef += uleb128ByteCount(typeAbbrevNumber) + (referenced == Primitive.VOID ? 0 : 4); // size of ref4
            }
            case Primitive primitive -> {
                Die d = new Die(DW_TAG_base_type, false,
                                new int[]{DW_AT_byte_size, DW_FORM_data1,
                                        DW_AT_encoding, DW_FORM_data1,
                                        DW_AT_name, DW_FORM_strp});
                int typeAbbrevNumber = abbrevNumber(dieMap, d);
                typeMap.put(t, nextTypeRef);
                nextTypeRef += 6 + uleb128ByteCount(typeAbbrevNumber); // size of data1*2+strp
                uleb128(out, typeAbbrevNumber); // abbreviation code

                printByte(out, (byte) Mcc.size(t));
                byte encoding= switch(primitive){
                    case CHAR -> DW_ATE_signed_char;
                    case UCHAR -> DW_ATE_unsigned_char;
                    case SCHAR -> DW_ATE_signed_char;
                    case INT -> DW_ATE_signed;
                    case UINT -> DW_ATE_unsigned;
                    case SHORT -> DW_ATE_signed;
                    case USHORT -> DW_ATE_unsigned;
                    case LONG -> DW_ATE_signed;
                    case ULONG -> DW_ATE_unsigned;
                    case LONGLONG -> DW_ATE_signed;
                    case ULONGLONG -> DW_ATE_unsigned;
                    case DOUBLE -> DW_ATE_float;
                    case FLOAT -> DW_ATE_float;
                    case VOID -> throw new AssertionError();
                    case BOOL -> DW_ATE_boolean;
                };
                printByte(out, encoding);
                addAndPrintString(out, topLevelAsms, primitive.name);
            }
            case Structure(boolean isUnion, String tag, StructDef _) -> {
                var structDef = Mcc.TYPE_TABLE.get(tag);
                if (structDef != null) {
                    for (var m : structDef.members()) {
                        Type mt = m.type();
                        nextTypeRef = addType(out, topLevelAsms,
                                dieMap, mt,
                                typeMap, nextTypeRef, arrayTypes, finalTypeMap);
                    }

                }
                if (isComplete(t)) {
                    long size = Mcc.size(t);
                    var dwForm = dataForm(size);
                    int dwFormSize = formSize(dwForm);
                    Die structDie = new Die(isUnion ? DW_TAG_union_type : DW_TAG_structure_type, true,
                            new int[]{
                                    DW_AT_name, DW_FORM_strp,
                                    DW_AT_byte_size, dwForm
                            });
                    int structDieAbbrevNumber = abbrevNumber(dieMap, structDie);

                    Die memberDie = new Die(DW_TAG_member, false,
                            new int[]{
                                    DW_AT_name,         DW_FORM_strp,
                                    DW_AT_type,         DW_FORM_ref4,
                                    DW_AT_data_member_location, dwForm});
                    int memberDieAbbrevNumber = abbrevNumber(dieMap, memberDie);


                    typeMap.put(t, nextTypeRef);

                    uleb128(out, structDieAbbrevNumber);
                    addAndPrintString(out, topLevelAsms, tag);
                    printWhatever(out, size, dwForm);

                    nextTypeRef += uleb128ByteCount(structDieAbbrevNumber) + 4 + dwFormSize; // size strp

                    for (var m : structDef.members()) {
                        uleb128(out, memberDieAbbrevNumber);
                        Type mt = m.type();
                        addAndPrintString(out, topLevelAsms, m.name());
                        if (finalTypeMap != null) {
                            printInt(out, finalTypeMap.get(mt));
                        }

                        printWhatever(out, m.byteOffset(), dwForm);
                        nextTypeRef += uleb128ByteCount(memberDieAbbrevNumber) + 8 + dwFormSize;
                    }
                    // no more members
                    printByte(out, (byte)0);
                    nextTypeRef++;

                } else {
                    Die structDie = new Die(isUnion ? DW_TAG_union_type : DW_TAG_structure_type, false,
                            new int[]{
                                    DW_AT_name, DW_FORM_strp,
                                    DW_AT_declaration, DW_FORM_flag_present
                            });
                    int structDieAbbrevNumber = abbrevNumber(dieMap, structDie);
                    uleb128(out, structDieAbbrevNumber);
                    addAndPrintString(out, topLevelAsms, tag);
                    typeMap.put(t, nextTypeRef);
                    nextTypeRef += uleb128ByteCount(structDieAbbrevNumber) + 4; // size strp

                }

            }


            default -> {}
                //throw new IllegalStateException("Unexpected value: " + t);

        }
        return nextTypeRef;
    }

    private static int formSize(int dwForm) {
        if (dwForm == DW_FORM_data1) return 1;
        if (dwForm == DW_FORM_data2) return 2;
        if (dwForm == DW_FORM_data4) return 4;
        if (dwForm == DW_FORM_data8) return 8;
        if (dwForm == DW_FORM_data16) return 16;
        throw new AssertionError();
    }

    /** {@return DW_FORM_dataN big enough for n} */
    private static int dataForm(long n) {
        int bitsRequired = 64-Long.numberOfLeadingZeros(n);
        if (bitsRequired <= 8) return DW_FORM_data1;
        if (bitsRequired <= 16) return DW_FORM_data2;
        if (bitsRequired <= 32) return DW_FORM_data4;
        return DW_FORM_data8;
    }

    /** return how many bytes it takes to represent l in uleb128 */
    private static int uleb128ByteCount(long l) {
        if (l < 0) return 10; // the highest one bit of the unsigned long is set so this takes 10 7-bit units to represent -> 10 bytes
        if (l == 0) return 1;
        int bitsNeeded = 64 - Long.numberOfLeadingZeros(l);
        if  (bitsNeeded % 7 == 0) return bitsNeeded / 7;
        return bitsNeeded / 7 + 1;
    }

    private static int sleb128ByteCount(long value) {
        int count = 0;
        boolean more = true;

        while (more) {
            byte b = (byte)(value & 0x7f);
            value >>= 7;

            boolean signBitSet = (b & 0x40) != 0;

            if ((value == 0 && !signBitSet) ||
                    (value == -1 && signBitSet)) {
                more = false;
            }

            count++;
        }

        return count;
    }

    public static void main(String[] args) {
        System.out.println(sleb128ByteCount(200));
    }

    private static void addAndPrintString(PrintWriter out, List<TopLevelAsm> topLevelAsms, String s) {
        String label = makeTemporary(".L");
        topLevelAsms.add(new DebugString(label, s));
        printIndent(out, ".long\t" + label);
    }


    private static int abbrevNumber(LinkedHashMap<Die, Integer> dieMap,
                                    Die d) {

        final int s = dieMap.size();
        return dieMap.computeIfAbsent(d, k -> s + 1);
    }

    private static void printInt(PrintWriter out, String s) {
        printIndent(out, ".int\t" + s);
    }

    private static void printShort(PrintWriter out, short s) {
        printIndent(out, ".value\t0x" + Integer.toHexString(s));
    }

    private static void printByte(PrintWriter out, byte b) {
        printIndent(out, ".byte\t0x" + Integer.toHexString(0xff & b));
    }
    private static void printQuad(PrintWriter out, long q) {
        printIndent(out, ".quad\t0x" + Long.toHexString(q));
    }

    private static void printWhatever(PrintWriter out, long n) {
        int bitsRequired = 64 - Long.numberOfLeadingZeros(n);
        if (bitsRequired <= 8) printByte(out, (byte) n);
        else if (bitsRequired <= 16) printShort(out, (short) n);
        else if (bitsRequired <= 32) printInt(out, (int) n);
        else printQuad(out, n);
    }

    private static void printWhatever(PrintWriter out, long n, int dw_form) {
        if (dw_form == DW_FORM_data1) printByte(out, (byte) n);
        else if (dw_form == DW_FORM_data2) printShort(out, (short) n);
        else if (dw_form == DW_FORM_data4) printInt(out, (int) n);
        else {
            assert dw_form == DW_FORM_data8;
            printQuad(out, n);
        }
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
    private static void sleb128(PrintWriter out, long l) {
        printIndent(out, ".sleb128\t" + l);
    }

    private static void uleb128s(PrintWriter out, int[] a) {
        for (int i : a)
            printIndent(out, ".uleb128\t0x" + Integer.toHexString(i));
    }

}
