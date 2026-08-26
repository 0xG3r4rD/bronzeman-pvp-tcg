package com.bronzemanpvptcg.model;

/**
 * One owned copy of a card within a {@link CardEntry} group (profile save schema).
 */
public final class CardVariant
{
	/** Omitted when false; absent or null means normal. */
	public Boolean foil;
	public String pulledBy;
	public Long pulledAt;
	/** Omitted when false. */
	public Boolean locked;
	/** Legacy profile save: expanded on load when present. */
	public Integer quantity;
	/** Legacy profile save: expanded on load when present. */
	public Integer lockedQuantity;
}
