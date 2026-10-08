package com.example.apothicenchantingterminal.integration;

/**
 * アイテムはレジストリが開いている間(登録イベントの中)にしか作れない。
 * このクラスは、AE2WTLibの端末登録イベントの中で初めて参照されるようにすること。
 * (MODのコンストラクタなど、それより前に参照すると "Registry is already frozen" で落ちる)
 */
public final class WtlibItems {

    public static final LibraryWTItem ITEM = new LibraryWTItem();

    private WtlibItems() {}
}
