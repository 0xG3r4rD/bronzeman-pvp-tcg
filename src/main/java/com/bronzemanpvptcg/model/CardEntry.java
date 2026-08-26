package com.bronzemanpvptcg.model;

import java.util.List;

/**
 * Owned copies of one card name, grouped for compact JSON (profile save schema).
 */
public final class CardEntry
{
	public String cardName;
	public List<CardVariant> variants;
}
