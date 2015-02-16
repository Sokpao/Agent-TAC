/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
 *
 * Copyright (c) 2001-2005 SICS AB. All rights reserved.
 *
 * SICS grants you the right to use, modify, and redistribute this
 * software for noncommercial purposes, on the conditions that you:
 * (1) retain the original headers, including the copyright notice and
 * this text, (2) clearly document the difference between any derived
 * software and the original, and (3) acknowledge your use of this
 * software in pertaining publications and reports.  SICS provides
 * this software "as is", without any warranty of any kind.  IN NO
 * EVENT SHALL SICS BE LIABLE FOR ANY DIRECT, SPECIAL OR INDIRECT,
 * PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSSES OR DAMAGES ARISING OUT
 * OF THE USE OF THE SOFTWARE.
 *
 * -----------------------------------------------------------------
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 23 April, 2002
 * Updated : $Date: 2005/06/07 19:06:16 $
 *	     $Revision: 1.1 $
 * ---------------------------------------------------------
 * DummyAgent is a simplest possible agent for TAC. It uses
 * the TACAgent agent ware to interact with the TAC server.
 *
 * Important methods in TACAgent:
 *
 * Retrieving information about the current Game
 * ---------------------------------------------
 * int getGameID()
 *  - returns the id of current game or -1 if no game is currently plaing
 *
 * getServerTime()
 *  - returns the current server time in milliseconds
 *
 * getGameTime()
 *  - returns the time from start of game in milliseconds
 *
 * getGameTimeLeft()
 *  - returns the time left in the game in milliseconds
 *
 * getGameLength()
 *  - returns the game length in milliseconds
 *
 * int getAuctionNo()
 *  - returns the number of auctions in TAC
 *
 * int getClientPreference(int client, int type)
 *  - returns the clients preference for the specified type
 *   (types are TACAgent.{ARRIVAL, DEPARTURE, HOTEL_VALUE, E1, E2, E3}
 *
 * int getAuctionFor(int category, int type, int day)
 *  - returns the auction-id for the requested resource
 *   (categories are TACAgent.{CAT_FLIGHT, CAT_HOTEL, CAT_ENTERTAINMENT
 *    and types are TACAgent.TYPE_INFLIGHT, TACAgent.TYPE_OUTFLIGHT, etc)
 *
 * int getAuctionCategory(int auction)
 *  - returns the category for this auction (CAT_FLIGHT, CAT_HOTEL,
 *    CAT_ENTERTAINMENT)
 *
 * int getAuctionDay(int auction)
 *  - returns the day for this auction.
 *
 * int getAuctionType(int auction)
 *  - returns the type for this auction (TYPE_INFLIGHT, TYPE_OUTFLIGHT, etc).
 *
 * int getOwn(int auction)
 *  - returns the number of items that the agent own for this
 *    auction
 *
 * Submitting Bids
 * ---------------------------------------------
 * void submitBid(Bid)
 *  - submits a bid to the tac server
 *
 * void replaceBid(OldBid, Bid)
 *  - replaces the old bid (the current active bid) in the tac server
 *
 *   Bids have the following important methods:
 *    - create a bid with new Bid(AuctionID)
 *
 *   void addBidPoint(int quantity, float price)
 *    - adds a bid point in the bid
 *
 * Help methods for remembering what to buy for each auction:
 * ----------------------------------------------------------
 * int getAllocation(int auctionID)
 *   - returns the allocation set for this auction
 * void setAllocation(int auctionID, int quantity)
 *   - set the allocation for this auction
 *
 *
 * Callbacks from the TACAgent (caused via interaction with server)
 *
 * bidUpdated(Bid bid)
 *  - there are TACAgent have received an answer on a bid query/submission
 *   (new information about the bid is available)
 * bidRejected(Bid bid)
 *  - the bid has been rejected (reason is bid.getRejectReason())
 * bidError(Bid bid, int error)
 *  - the bid contained errors (error represent error status - commandStatus)
 *
 * quoteUpdated(Quote quote)
 *  - new information about the quotes on the auction (quote.getAuction())
 *    has arrived
 * quoteUpdated(int category)
 *  - new information about the quotes on all auctions for the auction
 *    category has arrived (quotes for a specific type of auctions are
 *    often requested at once).
 *    
 * auctionClosed(int auction)
 *  - the auction with id "auction" has closed
 *
 * transaction(Transaction transaction)
 *  - there has been a transaction
 *
 * gameStarted()
 *  - a TAC game has started, and all information about the
 *    game is available (preferences etc).
 *
 * gameStopped()
 *  - the current game has ended
 *
 */

package se.sics.tac.aw;

import java.util.logging.Logger;

import se.sics.tac.util.ArgEnumerator;

@SuppressWarnings("static-access")
public class DummyAgent extends AgentImpl {

	private static final Logger log = Logger.getLogger(DummyAgent.class
			.getName());

	private static final boolean DEBUG = true;

	private static final int FLIGHT_THRESHOLD = 300; // initial bid price

	private static final int FLIGHT_MAXPRICE = 800;

	private static final int FLIGHT_TIMELIMIT = 600000; // ten minutes

	private static final int FLIGHT_PUNISHMENT = 100; // costs of changing date

	private static final int HOTEL_INCREMENT = 10;

	private static final float HOTEL_OVERBID_FACTOR = 1.5f;

	private static final int HOTEL_SATISFACTION_THRESHOLD = 100; // split in two
																	// groups

	private static final float HOTEL_MAXPRICE = 750; // maxiumum price per room
														// and night

	private float[] prices;

	protected void init(ArgEnumerator args) {
		prices = new float[agent.getAuctionNo()];
	}

	public void quoteUpdated(Quote quote) {
		int auction = quote.getAuction();
		int auctionCategory = agent.getAuctionCategory(auction);
		if (auctionCategory == TACAgent.CAT_HOTEL) {
			int alloc = agent.getAllocation(auction);
			if (alloc > 0 && quote.hasHQW(agent.getBid(auction))
					&& quote.getHQW() < alloc) {
				Bid bid = new Bid(auction);
				// Can not own anything in hotel auctions...
				// price pertubation fktn
				prices[auction] = quote.getAskPrice() * HOTEL_OVERBID_FACTOR
						+ (alloc - 1) * HOTEL_INCREMENT;
				// dont buy for a higher price than 500
				if (prices[auction] > HOTEL_MAXPRICE) {
					agent.setAllocation(auction, 0);
					prices[auction] = 0;
					alloc = 0;
					allocationUpdated(auction);
				}
				bid.addBidPoint(alloc, prices[auction]);
				if (DEBUG) {
					log.finest("submitting bid with alloc="
							+ agent.getAllocation(auction) + " own="
							+ agent.getOwn(auction));
				}
				agent.submitBid(bid);
			}
		} else if (auctionCategory == TACAgent.CAT_ENTERTAINMENT) {
			int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
			if (alloc != 0) {
				Bid bid = new Bid(auction);
				if (alloc < 0)
					prices[auction] = 200f - (agent.getGameTime() * 120f) / 720000;
				else
					prices[auction] = 50f + (agent.getGameTime() * 100f) / 720000;
				bid.addBidPoint(alloc, prices[auction]);
				if (DEBUG) {
					log.finest("submitting bid with alloc="
							+ agent.getAllocation(auction) + " own="
							+ agent.getOwn(auction));
				}
				agent.submitBid(bid);
			}
		} else if (auctionCategory == TACAgent.CAT_FLIGHT) {
			updateFlights(auction);
		}
	}

	public void quoteUpdated(int auctionCategory) {
		log.fine("All quotes for "
				+ agent.auctionCategoryToString(auctionCategory)
				+ " has been updated");
	}

	public void bidUpdated(Bid bid) {
		log.fine("Bid Updated: id=" + bid.getID() + " auction="
				+ bid.getAuction() + " state="
				+ bid.getProcessingStateAsString());
		log.fine("       Hash: " + bid.getBidHash());
	}

	public void bidRejected(Bid bid) {
		log.warning("Bid Rejected: " + bid.getID());
		log.warning("      Reason: " + bid.getRejectReason() + " ("
				+ bid.getRejectReasonAsString() + ')');
	}

	public void bidError(Bid bid, int status) {
		log.warning("Bid Error in auction " + bid.getAuction() + ": " + status
				+ " (" + agent.commandStatusToString(status) + ')');
	}

	public void gameStarted() {
		log.fine("Game " + agent.getGameID() + " started!");

		calculateAllocation();
		sendBids();
	}

	public void gameStopped() {
		log.fine("Game Stopped!");
	}

	public void auctionClosed(int auction) {
		log.fine("*** Auction " + auction + " closed!");
	}

	private void sendBids() {
		for (int i = 0, n = agent.getAuctionNo(); i < n; i++) {
			int alloc = agent.getAllocation(i) - agent.getOwn(i);
			float price = -1f;
			switch (agent.getAuctionCategory(i)) {
			case TACAgent.CAT_FLIGHT:
				if (alloc > 0) {
					price = FLIGHT_THRESHOLD;
				}
				break;
			case TACAgent.CAT_HOTEL:
				if (alloc > 0) {
					price = 200;
					prices[i] = 200f;
				}
				break;
			case TACAgent.CAT_ENTERTAINMENT:
				if (alloc < 0) {
					price = 200;
					prices[i] = 200f;
				} else if (alloc > 0) {
					price = 50;
					prices[i] = 50f;
				}
				break;
			default:
				break;
			}
			if (price > 0) {
				Bid bid = new Bid(i);
				bid.addBidPoint(alloc, price);
				if (DEBUG) {
					log.finest("submitting bid with alloc="
							+ agent.getAllocation(i) + " own="
							+ agent.getOwn(i));
				}
				agent.submitBid(bid);
			}
		}
	}

	private void calculateAllocation() {
		for (int i = 0; i < 8; i++) {
			int inFlight = agent.getClientPreference(i, TACAgent.ARRIVAL);
			int outFlight = agent.getClientPreference(i, TACAgent.DEPARTURE);
			int hotel = agent.getClientPreference(i, TACAgent.HOTEL_VALUE);
			int type;

			// Get the flight preferences auction and remember that we are
			// going to buy tickets for these days. (inflight=1, outflight=0)

			int auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT,
					TACAgent.TYPE_INFLIGHT, inFlight);
			agent.setAllocation(auction, agent.getAllocation(auction) + 1);
			auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT,
					TACAgent.TYPE_OUTFLIGHT, outFlight);
			agent.setAllocation(auction, agent.getAllocation(auction) + 1);

			// if the hotel value is greater than 250 we will select the
			// expensive hotel (type = 1)
			if (hotel > HOTEL_SATISFACTION_THRESHOLD) {
				type = TACAgent.TYPE_GOOD_HOTEL;
			} else {
				type = TACAgent.TYPE_CHEAP_HOTEL;
			}
			// allocate a hotel night for each day that the agent stays
			for (int d = inFlight; d < outFlight; d++) {
				auction = agent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
				log.finer("Adding hotel for day: " + d + " on " + auction);
				agent.setAllocation(auction, agent.getAllocation(auction) + 1);
			}

			int eType = -1;
			while ((eType = nextEntType(i, eType)) > 0) {
				auction = bestEntDay(inFlight, outFlight, eType);
				log.finer("Adding entertainment " + eType + " on " + auction);
				agent.setAllocation(auction, agent.getAllocation(auction) + 1);
			}
		}
	}

	private int bestEntDay(int inFlight, int outFlight, int type) {
		for (int i = inFlight; i < outFlight; i++) {
			int auction = agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, type,
					i);
			if (agent.getAllocation(auction) < agent.getOwn(auction)) {
				return auction;
			}
		}
		// If no left, just take the first...
		return agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, type, inFlight);
	}

	private int nextEntType(int client, int lastType) {
		int e1 = agent.getClientPreference(client, TACAgent.E1);
		int e2 = agent.getClientPreference(client, TACAgent.E2);
		int e3 = agent.getClientPreference(client, TACAgent.E3);

		// At least buy what each agent wants the most!!!
		if ((e1 > e2) && (e1 > e3) && lastType == -1)
			return TACAgent.TYPE_ALLIGATOR_WRESTLING;
		if ((e2 > e1) && (e2 > e3) && lastType == -1)
			return TACAgent.TYPE_AMUSEMENT;
		if ((e3 > e1) && (e3 > e2) && lastType == -1)
			return TACAgent.TYPE_MUSEUM;
		return -1;
	}

	// -------------------------------------------------------------------
	// Only for backward compability
	// -------------------------------------------------------------------

	public static void main(String[] args) {
		TACAgent.main(args);
	}

	// Flights

	/**
	 * Method is called, to analyze the trend of the flight-prices and updating
	 * the bids.
	 * 
	 * @param i
	 *            the auction id
	 */
	private void updateFlights(int i) {
		log.severe("--------------------------------------------------");
		int alloc = agent.getAllocation(i) - agent.getOwn(i);
		if (agent.getAuctionCategory(i) == TACAgent.CAT_FLIGHT) {
			if (alloc > 0) {
				log.severe("ASK:" + agent.getQuote(i).getAskPrice());
				// prices[i] = (((float) (agent.getGameTime() *
				// (FLIGHT_MAXPRICE))) / ((float) agent
				// .getGameLength()));

				int id = -1;
				if (agent.getGameTimeLeft() <= FLIGHT_TIMELIMIT) {
					id = checkForDateChange(i);
				}

				Bid bid = new Bid(id);
				bid.addBidPoint(alloc, prices[id]);
				if (DEBUG) {
					log.severe("submitting bid with alloc="
							+ agent.getAllocation(id) + " own="
							+ agent.getOwn(id));
				}
				agent.submitBid(bid);
			}
		}
	}

	/**
	 * Method checks, if its actually better to change the flight date, if the
	 * saved amount is greater than the clients dissatisfaction.
	 * 
	 * @param i
	 *            the auction id
	 * 
	 * @return the new auction id for the specific goods
	 */
	private int checkForDateChange(int i) {
		int alloc = agent.getAllocation(i) - agent.getOwn(i);
		if (agent.getAuctionCategory(i) == TACAgent.CAT_FLIGHT && alloc > 0) {
			int type = agent.getAuctionType(i);
			int change = 0;
			// fly one day later if inflight, one date earlier if outflight
			if (type == TACAgent.TYPE_INFLIGHT)
				change = 1;
			else if (type == TACAgent.TYPE_OUTFLIGHT)
				change = -1;

			int altauction = agent.getAuctionFor(agent.getAuctionCategory(i),
					agent.getAuctionType(i), agent.getAuctionDay(i) + change);
			// if the price difference is greater than the dissatisfaction value
			if (agent.getQuote(i).getAskPrice()
					- agent.getQuote(altauction).getAskPrice() > FLIGHT_PUNISHMENT) {
				// get flight on other day
				agent.setAllocation(altauction, agent.getAllocation(altauction)
						+ alloc);
				agent.setAllocation(i, agent.getAllocation(i) - alloc);

				// decrease allocation for hotels
				// for now it just decreases any allocs it finds (starting with
				// good hotel) independent of preferences
				int cheap = agent.getAuctionFor(TACAgent.CAT_HOTEL,
						TACAgent.TYPE_CHEAP_HOTEL, agent.getAuctionDay(i));
				int exp = agent.getAuctionFor(TACAgent.CAT_HOTEL,
						TACAgent.TYPE_GOOD_HOTEL, agent.getAuctionDay(i));
				int curralloc = agent.getAllocation(exp);
				if (curralloc >= alloc) {
					agent.setAllocation(exp, curralloc - alloc);
				} else {
					alloc = alloc - curralloc;
					agent.setAllocation(exp, 0);
					agent.setAllocation(cheap, agent.getAllocation(cheap)
							- alloc);
				}
				// inform of update
				allocationUpdated(cheap);
				allocationUpdated(exp);

				// change the auction id to handle
				i = altauction;
			}

		}
		prices[i] = FLIGHT_MAXPRICE;
		return i;

	}

	/**
	 * Method is called, if the allocation of the given auctions was updated.
	 * (E.g. the flight were scheduled different and the allocation for this
	 * hotels may have or may have not changed (increasing or decreasing))
	 * 
	 * @param id
	 *            the auction id
	 */
	private void allocationUpdated(int id) {
		int need = agent.getAllocation(id) - agent.getOwn(id);
		int bids = agent.getBid(id).getQuantity();
		if (need != bids) {
			Bid bid = new Bid(id);
			bid.addBidPoint(need, prices[id]);
			if (DEBUG) {
				log.finest("submitting bid with alloc="
						+ agent.getAllocation(id) + " own=" + agent.getOwn(id));
			}
			agent.replaceBid(agent.getBid(id), bid);
		}
	}
}