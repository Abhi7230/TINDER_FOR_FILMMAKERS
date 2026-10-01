# Reddit research: how indie filmmakers crew up, and why it breaks

Consolidated from nine research angles run on 1-2 Oct 2026 (raw reports in `research/raw/`). The angles: finding crew, flakes/trust/pay, existing platforms, role perspectives, beginners/students/India, the post-match workflow, how app ideas are received, profile/matching signals, and safety/growth/money.

- **Coverage:** about 237 Reddit searches and about 340 deep reads (post plus comments). The deep reads are summed across angles, and several important threads were read by more than one researcher, so the number of *unique* threads is lower (roughly 230). Subreddits: r/Filmmakers, r/filmmaking, r/acting, r/FilmIndustryLA, r/cinematography, r/LocationSound, r/editors, r/VideoEditing, r/vfx, r/filmscoring, r/Screenwriting, r/TVWriting, r/indianFilmmakers, Indian city subs (mumbai, pune, delhi, hyderabad, Chennai, kolkata), r/FilmIndustryLondon, r/AtlFilmmakers, r/crewwanted, r/CastingCalls, r/WeAreTheMusicMakers (as an analogue), r/startups. Most threads date from 2023 to 2026.
- **Caveats:** the shared Reddit helper hit HTTP 429 rate limits several times in every angle. Researchers paused, then read threads other researchers had cached; the data is real but the samples overlap. Composer-platform pain, AC/gaffer views and "directors looking for scripts" have thinner evidence. Searches across all of Reddit were mostly noise, so most evidence comes from subreddit-scoped searches.
- **Privacy:** no usernames are recorded. Private individuals accused in threads are not named. Quotes are verbatim and 25 words or fewer.

---

## 1. Executive summary

1. **The job isn't "find people". It's "make the shoot happen and keep the promises."** Open calls already get 20-67 "interested, DM me" replies and still never turn into shoots ([1tn4fw9](https://reddit.com/r/indianFilmmakers/comments/1tn4fw9/), [1qfck65](https://reddit.com/r/indianFilmmakers/comments/1qfck65/)). Staffing takes a connected LA producer "a day or two" and takes newcomers months or years ([7nh24j](https://reddit.com/r/Filmmakers/comments/7nh24j/), [1pw8crx](https://reddit.com/r/indianFilmmakers/comments/1pw8crx/)). The unit of value is a booked shoot date with a crew that actually shows up, not a match.
2. **Pay is the first thing on the card, or people don't reply.** "Collab", "exposure", "credit + IMDb + copy" and "DM for details" are read as code for unpaid work or a bait-and-switch, in about 56 thread mentions across 6 angles. Communities already enforce it themselves: r/filmscoring made paid/unpaid flair mandatory ([1h06fmg](https://www.reddit.com/r/filmscoring/comments/1h06fmg/)). An honest "Unpaid, here's what you get" beats vague perks.
3. **The worst broken promises come after the shoot.** Footage, final cut, credit and pay don't arrive. "The footage is our pay" for actors ([1infcka](https://www.reddit.com/r/acting/comments/1infcka/)). One actor did about 90 student shorts and got about 5 finished films back ([1k21cy3](https://www.reddit.com/r/acting/comments/1k21cy3/)). Nobody tracks any of it, so it is the clearest gap in the market.
4. **Unpaid means unreliable, in both directions.** Crews drop from 10 to 2 five days before a shoot ([1s7vlqc](https://reddit.com/r/Filmmakers/comments/1s7vlqc/)). Productions ghost actors at call time ([1vnwpl6](https://reddit.com/r/acting/comments/1vnwpl6/)). Even a token stipend or a written booking changes behaviour: "When money is on the line people stop playing with your time."
5. **Trust failures follow a few detectable patterns.** Scams and predators almost always do one of these: move the conversation off the platform, hold a private-home or 1:1 audition, send sexual-only sides, or ask the talent for money. Platform badges have made things worse: a Backstage listing "background checked" led an actor to undress at an audition ([1awhlch](https://reddit.com/r/acting/comments/1awhlch/)).
6. **Reputation already exists but stays private.** People say "we all have a list" and "just don't rehire, never say why" ([1kzesa0](https://www.reddit.com/r/Filmmakers/comments/1kzesa0/), [1ov4ary](https://reddit.com/r/Filmmakers/comments/1ov4ary/)). Public star reviews are feared as retaliation and as a defamation risk. Verified, aggregated facts (paid on time, footage delivered, showed up) are welcome.
7. **The incumbent is Facebook groups plus referrals, and people resent both.** Paid boards (StaffMeUp, Mandy, Backstage, Stage 32) are hated for charging job seekers. Past swipe apps are dead or tiny: Cinematcher and Daisie are gone, and BeScene has about 100 Android downloads and a 3.0 rating. Density and trust win, not features.
8. **The market is lopsided.** Hirers drown in oversupplied roles (about 8,000 submissions for 4 roles, [1kwxpw2](https://reddit.com/r/acting/comments/1kwxpw2/); 200 applicants and 15 qualified, [1axfd2z](https://reddit.com/r/FilmIndustryLA/comments/1axfd2z/)). Sound mixers, gaffers, ADs, producers, gear owners and, in India, editors and female actors are scarce and have to be pitched to. Hiring flows top-down: a DP or producer brings their own people.
9. **India is a different shape of the same problem.** It is hyperlocal (people pick by metro station), split by language (Hindi, Tamil, Telugu, Malayalam, Kannada, Bengali), weekend-only (IT jobs) and WhatsApp-native. Fee scams are rampant, Net 90 is "good days", and Android is decisive.
10. **"Tinder" framing is toxic in public.** The thread "Is there a Tinder for filmmakers?" immediately turned to dating jokes ([15sa7ji](https://reddit.com/r/Filmmakers/comments/15sa7ji/)). With predators posing as directors, face-first cards would drive away women. Keep the swipe, but make it about work: project, then role, then booking.

**What this means for the product:** cards that lead with terms, a match that ends in a two-tap booking card, a day sheet that works over WhatsApp, a wrap checklist that treats footage and credit like invoices, and private, fact-based reputation that makes rebooking easy. It should be free for talent, launch one city at a time, and be built for Android.

---

## 2. Top 12 pains, ranked by frequency × intensity

**How the ranking works:** "mentions" is the sum of the per-pain thread counts each researcher reported. The same thread can be counted in more than one angle, so treat mentions as relative weight, not unique threads. Intensity is the researchers' consensus: very high, high or medium-high.

| # | Pain | Mentions / angles | Intensity | Roles hit hardest |
|---|---|---|---|---|
| 1 | Footage, credit and final cut never delivered | ~52 / 7 | High (the deliverable *is* the pay) | Actors, ADs, DPs, writers, unpaid crew |
| 2 | Pay hidden or euphemistic ("collab", "exposure", "DM me") | ~56 / 6 | High | Everyone; sharpest for composers, sound, VFX, editors |
| 3 | Flaking, ghosting and dropouts, both ways | ~57 / 6 | High | Directors and producers; actors ghosted by productions |
| 4 | Non-payment, late pay, bait-and-switch, terms only in chat | ~60 / 5 | High | PAs, ADs, wardrobe, sound, post; India (Net 90) |
| 5 | Scams, impersonation and fees charged to talent | ~62 / 7 | High | New actors (US and India), green crew, writers |
| 6 | Newcomer chicken-and-egg: no network, no portfolio | ~55 / 4 | High | First-time directors and writers, career switchers, people new to a city |
| 7 | Vague posts and unknown logistics (dates, place, hours, travel, food) | ~56 / 4 | High | All crew; specialists; unpaid crew outside hubs |
| 8 | Predators and unsafe auditions or sets | ~30 / 5 | Very high | Women actors, teens, women crew |
| 9 | No local density (hub gap, empty rooms) | ~42 / 5 | High | Small towns, tier-2 cities, people new to a city |
| 10 | Hirer overload: applicant floods, noise, lopsided roles | ~44 / 6 | Medium-high | Line producers, casting directors, directors; applicants who hear nothing back |
| 11 | Disorganised sets and broken day-of communication | ~59 / 4 | Medium-high | DPs, sound, actors, PAs, volunteers |
| 12 | Pay-to-apply platforms and billing dark patterns | ~39 / 4 | High resentment | Crew, actors, writers |

### 1. Footage, credit and final cut never delivered
- **What hurts:** people work unpaid for "credit + copy" and wait 6 to 24 months, get ghosted ("lowkey cnt find it"), find the film was scrapped, or learn about the release from a friend. Credits are left out, misspelled or downgraded ("AE credit for a full edit"). Directors also won't hand over raw outtakes, so neither side has a standard.
- **Roles:** actors most, then ADs/PAs, DPs and camera operators, writers, editors.
- **Evidence:** ~52 mentions across 7 angles.
- **Quote:** "The footage is our pay. That the only reason the majority of us do student films." ([1infcka](https://www.reddit.com/r/acting/comments/1infcka/))
- **Links:** [1uc2pmc](https://www.reddit.com/r/acting/comments/1uc2pmc/), [1k21cy3](https://www.reddit.com/r/acting/comments/1k21cy3/), [1kjtsbu](https://www.reddit.com/r/acting/comments/1kjtsbu/), [1tjoetx](https://reddit.com/r/acting/comments/1tjoetx/), [1o5kfbd](https://www.reddit.com/r/Filmmakers/comments/1o5kfbd/), [1gvyywx](https://www.reddit.com/r/Filmmakers/comments/1gvyywx/), [1waupaq](https://www.reddit.com/r/acting/comments/1waupaq/), [1khzk7e](https://www.reddit.com/r/Filmmakers/comments/1khzk7e/)
- **Implication:** turn each deliverable into a dated item ("approved clips of your scenes by 15 Jan", never "raw footage") on the booking card. Track it on a wrap checklist both sides can see, and feed the result into the owner's track record.

### 2. Pay hidden or euphemistic
- **What hurts:** pay is left out entirely or replaced by perks. People "talk about talent before rates". Sound or music is the only unpaid department. A rich producer asks for volunteers. "Four jobs for $250/day." People also want pay parity: is anyone else on this project being paid?
- **Roles:** everyone. Sharpest for composers, location sound, VFX, editors, DPs with gear, writers.
- **Evidence:** ~56 mentions across 6 angles.
- **Quote:** "'Collaborate' is always code for unpaid work." ([1ovhqqk](https://www.reddit.com/r/Filmmakers/comments/1ovhqqk/))
- **Links:** [1kzesa0](https://www.reddit.com/r/Filmmakers/comments/1kzesa0/), [1v1nrip](https://www.reddit.com/r/filmscoring/comments/1v1nrip/), [u9seb6](https://www.reddit.com/r/LocationSound/comments/u9seb6/), [dvww8y](https://www.reddit.com/r/LocationSound/comments/dvww8y/), [1crwr9f](https://www.reddit.com/r/acting/comments/1crwr9f/), [1udttys](https://www.reddit.com/r/editors/comments/1udttys/), [1f23l1v](https://www.reddit.com/r/LocationSound/comments/1f23l1v/), [1wj0n3r](https://reddit.com/r/indianFilmmakers/comments/1wj0n3r/)
- **Implication:** a required, structured compensation block on every role (Paid / Stipend / Unpaid), with amount, kit fee and payment terms, shown first on the card. Unpaid roles must list what people get. Credit, IMDb or a copy can't be the only compensation. Show "X of Y roles paid".

### 3. Flaking, ghosting and dropouts, both ways
- **What hurts:** mass dropouts days before a shoot. "I'd love to!" with no follow-through. Dropped soft holds. Collaborators who drift off after one project ("one-timers") and the collaborator cliff after college. In the other direction: productions ghost actors at call time, cut a week's booking to 2 days on arrival, and re-offer the role as unpaid. Applicants get no closure at all.
- **Roles:** no- and low-budget directors and producers, 48-hour teams, student films; actors on the receiving end.
- **Evidence:** ~57 mentions across 6 angles.
- **Quote:** "90% of the crew I had hired started to bail out 5 days before... down to 2 from 10" ([1s7vlqc](https://reddit.com/r/Filmmakers/comments/1s7vlqc/))
- **Links:** [1koxl7u](https://reddit.com/r/filmmaking/comments/1koxl7u/), [1bdo28y](https://reddit.com/r/Filmmakers/comments/1bdo28y/), [1vnwpl6](https://reddit.com/r/acting/comments/1vnwpl6/), [1pw8crx](https://reddit.com/r/indianFilmmakers/comments/1pw8crx/), [1wqp9pv](https://reddit.com/r/Filmmakers/comments/1wqp9pv/), [1rdzhzw](https://reddit.com/r/Filmmakers/comments/1rdzhzw/), [1gqihq7](https://reddit.com/r/acting/comments/1gqihq7/), [1mbu4zn](https://reddit.com/r/Filmmakers/comments/1mbu4zn/)
- **Implication:** commitment devices for both sides. A booking card accepted with a 72-hour notice pledge. 48-hour "still coming?" confirmations. A no-fault withdrawal flow. A backup bench and one-tap backfill. Automatic closure for every applicant. Reliability records for owners as well as crew.

### 4. Non-payment, late pay and bait-and-switch
- **What hurts:** paid jobs become unpaid on day 1 ("spent too much on sets"). A booking is cut after the crew member has turned down other gigs. Deal memos are ignored. One UK serial non-payer owed £33k to 30+ people and kept hiring. Net 30/60/90 is pushed onto freelancers. Deferred pay "never" pays. Terms live only in chat, so roles (1st vs 2nd AD), day counts and pay shift and can't be proven.
- **Roles:** PAs, ADs, production designers, wardrobe, DPs, mixers, post freelancers. India: "Net 90 on good days".
- **Evidence:** ~60 mentions across 5 angles.
- **Quote:** "I am not getting paid and I never signed anything, all of this was agreed through chat." ([1m8jq9h](https://reddit.com/r/Filmmakers/comments/1m8jq9h/))
- **Links:** [1mp1xdb](https://www.reddit.com/r/FilmIndustryLA/comments/1mp1xdb/), [1w75fyd](https://www.reddit.com/r/Filmmakers/comments/1w75fyd/), [1aycshu](https://www.reddit.com/r/Filmmakers/comments/1aycshu/), [1um8uk5](https://www.reddit.com/r/Filmmakers/comments/1um8uk5/), [1sztuju](https://www.reddit.com/r/Filmmakers/comments/1sztuju/), [1u6jsx7](https://www.reddit.com/r/Filmmakers/comments/1u6jsx7/), [69z2hv](https://www.reddit.com/r/Filmmakers/comments/69z2hv/), [1o8s35i](https://www.reddit.com/r/Filmmakers/comments/1o8s35i/)
- **Implication:** a plain-language booking card accepted by **whoever controls the money**, with a pay-by date. Any change after booking shows a diff and needs re-acceptance. A wrap item for "paid", which the payee can mark "not received". Late or unpaid patterns attach to the *person*, not their LLC ("start a new company every film", [1mp1wpp](https://www.reddit.com/r/Filmmakers/comments/1mp1wpp/)).

### 5. Scams, impersonation and fees charged to talent
- **What hurts:**
  - India: "registration/security/portfolio" fees of Rs 500-8,000, paid-workshop upsells, Instagram ads impersonating TV channels.
  - US: fake-check PA scams built from Mandy, Backstage and FilmFreeway profile data; fake "Oscar-winning director" travel-fee scams sent to many actors at once; "$200/hr mentorship" grifts; a $40k "team fee" long con.
  - Platforms ignore reports.
- **Roles:** new actors (US and India), green crew and PAs, writers, first-time producers. Honest directors also suffer, because legitimate no-budget projects look like scams.
- **Evidence:** ~62 mentions across 7 angles (overlaps with pain 8).
- **Quote:** "There's no such thing as registration fee or security fee." ([1tumcb1](https://reddit.com/r/acting/comments/1tumcb1/))
- **Links:** [1vgzfim](https://reddit.com/r/mumbai/comments/1vgzfim/), [1m26l4w](https://reddit.com/r/acting/comments/1m26l4w/), [1bf2gmv](https://reddit.com/r/Filmmakers/comments/1bf2gmv/), [1q6pce4](https://reddit.com/r/Filmmakers/comments/1q6pce4/), [1sh0s2x](https://www.reddit.com/r/acting/comments/1sh0s2x/), [1gs3x5g](https://reddit.com/r/Filmmakers/comments/1gs3x5g/), [1pwa58f](https://www.reddit.com/r/Filmmakers/comments/1pwa58f/), [1l1fn2v](https://reddit.com/r/MalayalamMovies/comments/1l1fn2v/)
- **Implication:**
  - Never allow money requested from talent: block it in listings and flag it in chat.
  - Mask contact details until booked, so profiles can't be scraped.
  - Verify the person, not the company.
  - Word badges honestly ("ID verified", never "project vetted").

### 6. Newcomer chicken-and-egg: no network, no portfolio
- **What hurts:** strangers won't commit without a track record, and you can't build one without people. A DP and editor with about 200 credits posted in two 3,000-member local groups for two years and heard nothing. Film school's main product is the peer network: 58% of surveyed alumni named it, and about 46% got their first paid job through peers or alumni ([1usmaqf](https://reddit.com/r/Filmmakers/comments/1usmaqf/)). Credits don't carry over between countries. Introverts struggle with cliquey mixers and cold follow-ups.
- **Roles:** first-time writer-directors, writers without technical skills, IT-to-film career switchers, people who have just moved, introverts.
- **Evidence:** ~55 mentions across 4 angles.
- **Quote:** "The only good substitute for money is friends... without a previous body of work, it's entirely unreasonable to expect unpaid strangers to take you seriously" ([1mbu4zn](https://reddit.com/r/Filmmakers/comments/1mbu4zn/))
- **Links:** [1q18dgl](https://reddit.com/r/Filmmakers/comments/1q18dgl/), [74567o](https://reddit.com/r/Filmmakers/comments/74567o/), [1uhya0h](https://reddit.com/r/indianFilmmakers/comments/1uhya0h/), [1pk790z](https://reddit.com/r/indianFilmmakers/comments/1pk790z/), [1hw08t3](https://reddit.com/r/Filmmakers/comments/1hw08t3/), [1r81vkk](https://reddit.com/r/FilmIndustryLondon/comments/1r81vkk/), [1uqg858](https://reddit.com/r/Filmmakers/comments/1uqg858/), [1vs0tnc](https://reddit.com/r/indianFilmmakers/comments/1vs0tnc/)
- **Implication:**
  - Level tags with peer matching by default.
  - Step-up roles as a matching signal.
  - A "honest zero-budget" project label and a shoot plan that reads as legitimate.
  - Credits confirmed by both sides, so a first booking becomes portable proof.
  - One-tap specific asks instead of cold DMs.

### 7. Vague posts and unknown logistics
- **What hurts:** posts leave out dates, budget, runtime, scope, crew size and the real shoot location (which can differ from where the story is set). Distance, carpool or lodging, hours per day and meals are also missing. Role titles are confused ("Producer/PA" that is really an AD). Specialists can't judge fit without their own key facts: minutes of music, number of VFX shots, hours of footage, boom op budgeted.
- **Roles:** everyone deciding whether to reply; composers, editors, VFX, sound; unpaid crew outside hubs.
- **Evidence:** ~56 mentions across 4 angles.
- **Quote:** "Find out: what is my role? What is the rate? Is it out of town? Am I carpooling or can you cover mileage?" ([1v4eyq0](https://www.reddit.com/r/Filmmakers/comments/1v4eyq0/))
- **Links:** [1wi3qhz](https://reddit.com/r/AtlFilmmakers/comments/1wi3qhz/), [1v1nrip](https://www.reddit.com/r/filmscoring/comments/1v1nrip/), [1udttys](https://www.reddit.com/r/editors/comments/1udttys/), [1tzyoov](https://www.reddit.com/r/vfx/comments/1tzyoov/), [1uvl7tk](https://www.reddit.com/r/cinematography/comments/1uvl7tk/), [1vpfkp0](https://www.reddit.com/r/Filmmakers/comments/1vpfkp0/), [1m8jq9h](https://reddit.com/r/Filmmakers/comments/1m8jq9h/), [1kzesa0](https://www.reddit.com/r/Filmmakers/comments/1kzesa0/)
- **Implication:** a project card that can't be published half-empty: dates and days, hours cap, area, transport, meals, lodging, script status, who's attached. Add a role taxonomy with plain-language duties and scope chips, and a few role-specific fields.

### 8. Predators and unsafe auditions or sets
- **What hurts:**
  - Self-cast "directors" with sexual-only sides, private-residence callbacks, requests to move off the casting site, and requests for bikini photos or measurements.
  - Nudity at audition, and intimacy-coordinator clauses that get ignored.
  - "I direct short films" used as a pickup line, with texts after 11pm (Hyderabad).
  - A trafficking-like shoot that banned cast from swapping contacts.
  - No HR on indie sets.
- **Roles:** women actors (especially aged 17-25), new actors including teens, women crew.
- **Evidence:** ~30 mentions across 5 angles.
- **Quote:** "A normal casting will be in a public place with a mixed group and any deviation to a private place or 1-1 is NOT ok." ([165ao2t](https://www.reddit.com/r/Filmmakers/comments/165ao2t/))
- **Links:** [1awhlch](https://reddit.com/r/acting/comments/1awhlch/), [1vbtcdq](https://reddit.com/r/acting/comments/1vbtcdq/), [1u0pvc3](https://reddit.com/r/acting/comments/1u0pvc3/), [16uk3bw](https://www.reddit.com/r/Filmmakers/comments/16uk3bw/), [1jgaubm](https://reddit.com/r/hyderabad/comments/1jgaubm/), [1sl7qar](https://reddit.com/r/acting/comments/1sl7qar/), [1crwr9f](https://www.reddit.com/r/acting/comments/1crwr9f/), [1m86onr](https://www.reddit.com/r/acting/comments/1m86onr/)
- **Implication:**
  - Required content flags with safety measures.
  - Audition venue rules: self-tape, video, or a public or studio space with others present; never a private home.
  - Off-platform and body-request warnings in chat.
  - Work-first cards, with actor headshots shown only on gigs the actor applied to.
  - Visible report and block.
  - "Bring a friend welcome" as a norm.

### 9. No local density: the hub gap and empty rooms
- **What hurts:** outside LA, NYC, London and Mumbai, crew calls are rare and mostly unpaid. Local Facebook groups are "dead quiet". Local subs are tiny: Upstate_NY_Filmmakers has 533 members and its crew calls get 0 comments, and r/CastingCalls has about 1.1k members. Reddit is global, so replies come from five states and another country. New apps die of "only 2 ppl near me". The standard advice is "move to Mumbai."
- **Roles:** small-town US filmmakers (Arkansas, the Deep South, upstate NY), Indian tier-2 and tier-3 cities (Dehradun, Indore, Amravati, Kochi), people who have just moved.
- **Evidence:** ~42 mentions across 5 angles.
- **Quote:** "In my city.. Even the filmmaker Facebook group is dead quiet" ([15sa7ji](https://reddit.com/r/Filmmakers/comments/15sa7ji/))
- **Links:** [1w8sz4v](https://reddit.com/r/Filmmakers/comments/1w8sz4v/), [74567o](https://reddit.com/r/Filmmakers/comments/74567o/), [1vi1nvy](https://reddit.com/r/acting/comments/1vi1nvy/), [1vpwkqd](https://reddit.com/r/indianFilmmakers/comments/1vpwkqd/), [1vuauon](https://reddit.com/r/indianFilmmakers/comments/1vuauon/), [1vhr8kq](https://reddit.com/r/indianFilmmakers/comments/1vhr8kq/), [4q7fgz](https://reddit.com/r/WeAreTheMusicMakers/comments/4q7fgz/), [73gumt](https://reddit.com/r/WeAreTheMusicMakers/comments/73gumt/)
- **Implication:** launch city by city with honest density counts ("23 people near you"). Use neighbourhood-level radius. Default post roles (editor, composer, colorist, VFX) to remote, so thin markets still get matches. Label fallback results "outside your radius."

### 10. Hirer overload: applicant floods, noise and lopsided roles
- **What hurts:** 150-400+ applicants within hours. 8,000 submissions for 4 roles. Copy-paste messages. List views that hide qualifications. Hirers give up and go back to referrals. Applicants feel unseen ("top 10%, never a reply") and race to apply "in the first 15 minutes". Supply is lopsided: actors, writers, composers and junior editors flood in, while sound, gaffers, ADs and producers are scarce. Department heads hire their own teams.
- **Roles:** line producers, PMs, casting directors, directors (hiring side); applicants in the black hole.
- **Evidence:** ~44 mentions across 6 angles.
- **Quote:** "for every listing I post I get 200 responses of which maybe 15 are qualified" ([1axfd2z](https://reddit.com/r/FilmIndustryLA/comments/1axfd2z/))
- **Links:** [1b2h5fh](https://reddit.com/r/FilmIndustryLA/comments/1b2h5fh/), [1kwxpw2](https://reddit.com/r/acting/comments/1kwxpw2/), [13thwzl](https://www.reddit.com/r/Filmmakers/comments/13thwzl/), [dvww8y](https://www.reddit.com/r/LocationSound/comments/dvww8y/), [1hw08t3](https://reddit.com/r/Filmmakers/comments/1hw08t3/), [7nh24j](https://reddit.com/r/Filmmakers/comments/7nh24j/), [1v1nrip](https://www.reddit.com/r/filmscoring/comments/1v1nrip/), [1b5xk6q](https://reddit.com/r/cinematography/comments/1b5xk6q/)
- **Implication:**
  - Hard eligibility gates and application caps per role.
  - A daily like cap for applicants.
  - A ranked, capped shortlist for hirers, with reason chips.
  - Matching that changes by role scarcity: hirers scout for scarce roles and filter for oversupplied ones.
  - Later, crew packages, so a DP can bring a team.

### 11. Disorganised sets and broken day-of communication
- **What hurts:**
  - Scripts rewritten on the shoot day; shot lists sent the night before.
  - No location or wardrobe photos for DP and sound.
  - Everyone given the same call time; nobody knows when wrap is.
  - Call sheets posted in a Discord nobody joined, with addresses you can't tap.
  - Reschedules and cancellations never pushed; an actor showed up to nothing.
  - No food, no bathrooms; 3-hour unpaid drives with no carpool.
- **Roles:** DPs, sound, actors, PAs, volunteer crew.
- **Evidence:** ~59 mentions across 4 angles.
- **Quote:** "PLEASE PUT THE LOCATION ADDRESS IN THE BODY OF THE EMAIL, SO WE CAN CLICK ON IT" ([1udy93i](https://reddit.com/r/Filmmakers/comments/1udy93i/))
- **Links:** [19aonr1](https://reddit.com/r/Filmmakers/comments/19aonr1/), [1n3o1k3](https://reddit.com/r/cinematography/comments/1n3o1k3/), [u9seb6](https://reddit.com/r/LocationSound/comments/u9seb6/), [1vpfkp0](https://reddit.com/r/Filmmakers/comments/1vpfkp0/), [1f0hi8n](https://reddit.com/r/Filmmakers/comments/1f0hi8n/), [1ts9tma](https://reddit.com/r/filmmaking/comments/1ts9tma/), [1w3wrej](https://reddit.com/r/Filmmakers/comments/1w3wrej/), [1wqp9pv](https://reddit.com/r/Filmmakers/comments/1wqp9pv/)
- **Implication:**
  - A day sheet with staggered call times and a tappable address.
  - "Got it" acknowledgements and a change broadcast.
  - Share to WhatsApp and add to calendar.
  - Logistics fields on the card.
  - Don't build a StudioBinder clone: "StudioBinder hasn't killed the Google Doc call sheet."

### 12. Pay-to-apply platforms and billing dark patterns
- **What hurts:**
  - Pricing: StaffMeUp gives 5 lifetime tokens, then $19.99/mo; Mandy is about GBP 17/mo; Backstage $24.95/mo; Stage 32 $39/mo plus $200-400 "executive" sessions.
  - Fee stacking: actors pay about $350/yr across three sites.
  - Dark patterns: Casting Networks' storage gating drew a class action; StaffMeUp kept charging after account deletion; Daisie charged after it shut down; Coverfly closed and portfolios were lost.
- **Roles:** early- and mid-career crew, non-union actors, screenwriters.
- **Evidence:** ~39 mentions across 4 angles.
- **Quote:** "There's something about charging unemployed people to apply to jobs that doesn't sit right" ([1axfd2z](https://reddit.com/r/FilmIndustryLA/comments/1axfd2z/))
- **Links:** [1ffcdvf](https://reddit.com/r/Filmmakers/comments/1ffcdvf/), [15346t6](https://reddit.com/r/Screenwriting/comments/15346t6/), [1s4tj61](https://reddit.com/r/TVWriting/comments/1s4tj61/), [6i4asn](https://reddit.com/r/acting/comments/6i4asn/), [1c309zd](https://reddit.com/r/acting/comments/1c309zd/), [1sczvs3](https://reddit.com/r/acting/comments/1sczvs3/), [10f9ctx](https://reddit.com/r/Filmmakers/comments/10f9ctx/), [1iugy2h](https://reddit.com/r/Filmmakers/comments/1iugy2h/)
- **Implication:** free forever to be found, apply, match and message. No paid boosts or badges, and never charge for verification. One-tap data export. Publish how the app makes money from day one. Being free to apply also keeps the app clear of California's Krekorian Act rules on advance-fee talent services (confirm with a lawyer).

---

## 3. Also significant (outside the top 12)

| Pain | Evidence | Why it matters for design |
|---|---|---|
| **Reputation is private and fragile.** Whisper networks, fear of retaliatory reviews, one AD trashing a DP to a new crew, warnings that arrive only after you've taken the job | ~24 mentions / 4 angles. [1ov4ary](https://reddit.com/r/Filmmakers/comments/1ov4ary/), [1n3o1k3](https://reddit.com/r/cinematography/comments/1n3o1k3/), [1um8uk5](https://reddit.com/r/Filmmakers/comments/1um8uk5/), [1kzesa0](https://www.reddit.com/r/Filmmakers/comments/1kzesa0/) | No public stars. Collect structured, double-blind signals and show only aggregates of facts, with a minimum sample. "Would work again" stays private and drives rebooking. |
| **Fragmented, hated channels and open calls that don't convert:** Facebook approvals over a week, buried posts, WhatsApp groups where newcomers can't see history, Instagram DM limbo, Reddit DMs failing, Telegram paused in India in mid-2026 | ~42 mentions / 4 angles. [1rvrnsu](https://reddit.com/r/FilmIndustryLA/comments/1rvrnsu/), [1hw08t3](https://reddit.com/r/Filmmakers/comments/1hw08t3/), [1oxuxth](https://reddit.com/r/Chennai/comments/1oxuxth/), [1u51cvf](https://reddit.com/r/indianFilmmakers/comments/1u51cvf/), [1vhr8kq](https://reddit.com/r/indianFilmmakers/comments/1vhr8kq/) | Reliable in-app messaging up to the booking. After that, share out to WhatsApp rather than trying to replace it. One-tap cross-posting of project cards to FB, Instagram and WhatsApp groups. |
| **Unpaid spec work to apply:** prescreens, custom composer demos, trial edits, cover letters for unpaid roles | ~16 mentions / 4 angles. [1vtihev](https://www.reddit.com/r/filmscoring/comments/1vtihev/), [1m86onr](https://www.reddit.com/r/acting/comments/1m86onr/), [1rdzhzw](https://reddit.com/r/Filmmakers/comments/1rdzhzw/) | Applying is a reel link plus one line. Unpaid roles can't require demos or prescreens. |
| **Credit theft and IP overreach (writers, composers):** "Screenplay Adaptation of a Screenplay", short-film contracts grabbing feature rights, riff-rights clauses | ~6 mentions / 1 angle. [1i00yz2](https://reddit.com/r/Screenwriting/comments/1i00yz2/), [1jseq7k](https://reddit.com/r/Screenwriting/comments/1jseq7k/) | Exact credit wording on the booking card. Terms of service never claim rights to uploads. Script registration later. |
| **Mismatched commitment, level and taste:** one-timers vs long-term crew, beginners wanting peers, director and DP taste | ~20 mentions / 3 angles. [1pw8crx](https://reddit.com/r/indianFilmmakers/comments/1pw8crx/), [1pk790z](https://reddit.com/r/indianFilmmakers/comments/1pk790z/), [15f5w6t](https://reddit.com/r/Screenwriting/comments/15f5w6t/) | Level tags, taste (5 favourite films), goals, and a "keep / rebook" loop after wrap. |
| **Launch fatigue and AI distrust:** "another one", vibe-coded app spam, fear of training on NDA scripts | ~17 mentions / 2 angles. [1r6tsu7](https://reddit.com/r/Filmmakers/comments/1r6tsu7/), [1to61sh](https://reddit.com/r/FilmIndustryLA/comments/1to61sh/), [1muqlpe](https://reddit.com/r/Filmmakers/comments/1muqlpe/), [1rn1zit](https://reddit.com/r/Filmmakers/comments/1rn1zit/) | No gen-AI on users' work and no AI-written replies. Do one thing well. A visible practitioner founder. Never launch with promo posts. |

---

## 4. What people use today, and why it fails

### 4.1 Channels and workarounds

| Channel / workaround | Where it works | Why it fails |
|---|---|---|
| **Word of mouth, referrals, call-sheet contacts; hire a producer or DP who brings the team** | How pros actually hire: "I could have a show crewed in a day or two" ([7nh24j](https://reddit.com/r/Filmmakers/comments/7nh24j/)) | Closed to newcomers, transplants and small towns. Lives in people's heads. Shrinks when your network leaves the business ([1vm25ue](https://reddit.com/r/editors/comments/1vm25ue/)). Moves the problem up a level (how do you find the first producer?). |
| **Local and niche Facebook groups** | The #1 channel almost everywhere, "an incredible resource" ([1hw08t3](https://reddit.com/r/Filmmakers/comments/1hw08t3/)) | Resented ("only reason I still use FB"). The algorithm buries posts, approvals take over a week, scam floods, dead small-city groups, no structure, vetting or availability. Experienced crew don't post there. |
| **WhatsApp / Telegram / Discord groups** (the default in India) | Fast; everyone has WhatsApp | No project structure; newcomers can't see history; groups go inactive and multiply; phone numbers exposed; Telegram paused in India mid-2026 ([1u51cvf](https://reddit.com/r/indianFilmmakers/comments/1u51cvf/)) |
| **Instagram DMs and casting pages** | Reach; the default for Indian casting | The main scam channel; you can't tell who's open to collaborating; DM limbo; distracting ("suddenly you're watching reels", [1vhr8kq](https://reddit.com/r/indianFilmmakers/comments/1vhr8kq/)) |
| **Reddit open calls, city subs, collab subs** (r/crewwanted, r/CastingCalls) | Raw demand is visible | Global audience, piles of "DM me" replies, DMs fail, joke threads on city subs, collab subs of about 340-1,100 members |
| **Meetup, mixers, festivals, screenings** (MAMI, FHF Regal, Versova nights, PIFF, BISFF) | Real local trust; groups lasting 8 years ([15sa7ji](https://reddit.com/r/Filmmakers/comments/15sa7ji/)) | Infrequent, cliquey, hard for introverts, metro-only, conversations rarely become projects |
| **48-hour films, Kino cells, co-ops, IFP 50-Hour** | Deadlines force teams to form | Teams are formed in a panic a week out; brutal dropouts ([1s7vlqc](https://reddit.com/r/Filmmakers/comments/1s7vlqc/)) |
| **Film schools, thesis films, acting studios, theatre groups** | Ready-made peer networks; acting studios supply actors who need reel material | Only for people enrolled; the post-college cliff; cliques reuse the same 4-5 people |
| **Film commissions, rental houses** | Crew databases and bulletin boards in smaller markets | Static, hard to find, unknown to newcomers |
| **DIY deal memos and contracts** (footage within 6 months or $500; 50% deposit; Net 15 plus late fees) | Experienced actors and crew | Rewritten by hand every time; newcomers don't know to ask; unenforceable at micro budgets; "contracts for freelancers just don't exist" in parts of Asia ([1um8uk5](https://reddit.com/r/Filmmakers/comments/1um8uk5/)) |
| **Name-and-shame posts; private "do not call" lists** | Victims; department heads | Only after the harm; legal threats; not searchable at the moment of booking; never aggregated |
| **Call-sheet tools** (Google Sheets templates, StudioBinder, Movie Magic) | Making call sheets | Distribution fails (wrong channel, untappable address, no change alerts). StudioBinder is "buggy and expensive"; crew say "just give me a pdf" ([19aonr1](https://reddit.com/r/Filmmakers/comments/19aonr1/)) |
| **Going solo / friends and family** | The default fallback | Caps ambition, burns people out |

### 4.2 Platforms (merged competitor table)

| Platform | What it is | Cost to talent | Why it fails users | Gap for us |
|---|---|---|---|---|
| **StaffMeUp** | Job board (unscripted, doc, post) | 5 lifetime tokens, then $19.99/mo | Pay-to-apply; 150-400+ applicants per post; opaque months-in-role ranking; 2024 redesign broke multi-role and multi-city search; billing after deletion; Android app last updated 2019 (2.3 rating) | Free to apply; capped shortlists; a real Android app |
| **Mandy / Backstage / StarNow** (one company, owned by Cast & Crew) | Casting plus crew listings | About GBP 17/mo; $24.95/mo | "Fine for actors, useless for crew"; mostly no-budget; prescreens; broken filters; profile data harvested for scams; reports ignored; a "background checked" label that failed ([1awhlch](https://reddit.com/r/acting/comments/1awhlch/)) | Verified people; scam-resistant chat; honest badges |
| **Actors Access / Casting Networks / Spotlight** | Union and pro casting | Per-submission fees or about $68-235/yr | Fee stacking; storage-gating class action ([1c309zd](https://reddit.com/r/acting/comments/1c309zd/)) | Never make people pay to be seen |
| **ProductionHUB** | Crew and vendor directory | $6.99-109/mo to be featured | Manual vetting funnel; unclear freshness | Availability and rate on every card; "active this week" |
| **Stage 32** | Social network plus paid pitch sessions | $39/mo; $200-400 sessions | "Executives" turn out to be assistants; fake producers; unsubscribe ignored | Never sell access to gatekeepers |
| **Production Beast / ProductionList / USProductionNews** | Lead databases | $4-70/mo | Pushes cold outreach onto crew | Mutual-intent matching |
| **Nova (itsnova)** | Talent marketplace for brands | Free for talent | Brand and commercial focus; iOS/web only | Indie narrative; Android |
| **CREW UP** | Crew search, holds, calendar | n/a | iOS only; small | Android-first holds and availability |
| **Crew United / Shooting People / Team4Set** | European crew databases | About EUR 4/mo | Country-specific | Start where incumbents are weak (India) |
| **BeScene (ReelCall)** | Tinder-style connect/skip for film creatives | Free; $4.99/mo premium; paid Boosts | About 100+ Android downloads, 3.0 rating; empty state filter; no credits or links | Project-and-role cards, real credits, no paid boosts |
| **Liaise** | Creator collab app with contracts | $9.99/mo; $9.99-99.99 boosts; pay to expedite | Launch post got 0 comments; Android late | Free core; booking card without pay-to-jump-the-queue |
| **Credits / Filmclusive / Share** | All-in-one indie platforms (2025-26) | Free / $9.99 to submit | Mocked as "vibe coded", "no real users"; Share can't be cancelled on the site | Do one thing; real projects on day one |
| **SlateHub / FILMD / UMDB** | Free networks, member maps, 48HFP city rooms | Free | New, unproven; UMDB is working Toronto 48HFP (Oct 2026) | Better mobile matching and follow-through |
| **Upwork / Fiverr** | Remote post freelancing | Fees on both sides | Race to the bottom | Rate transparency; local-first on-set roles |
| **LinkedIn / Craigslist** | General | Free | "LinkedIn means nothing to industry professionals"; Craigslist is unpaid, scammy and has no profiles | Credits and reels first; mandatory pay |

### 4.3 The graveyard (why swipe and network apps die)
Cinematcher (swipe for film jobs, dead around 2019) · Daisie (raised $2.5M, 100K members, pivoted to courses, dead Aug 2024, charged users after shutdown) · Indie: Swipe-Based Casting (App Store 404) · Coverfly (shut by its parent, Aug 2025) · Filmshape (4 years to launch, then silence) · Networked Film (2012) · Technicolor Creative District (2014) · VIBBIDI · Wreckamovie · Beazy Gang · WOCOO · One Night Band and Vampr (musician analogues: 447 upvotes, then "only 2 ppl near me", iOS-only and forced Facebook login) ([app-idea-reception](raw/app-idea-reception.md), [existing-platforms](raw/existing-platforms.md)).

**Pattern:** the idea gets upvotes ("I need this so bad"), and then the app dies of an empty room, a flood of unqualified beginners, pay-to-play suspicion, or a scope that tries to do everything. Upvotes are not demand.

---

## 5. What makes a stranger say yes (ranked by how often it came up)
1. **Pay, even a stipend.** "If you don't have a network, you need to pay. Simple." ([1koxl7u](https://reddit.com/r/filmmaking/comments/1koxl7u/)). Actors deliberately apply only to *paid* student films as a commitment signal ([1infcka](https://reddit.com/r/acting/comments/1infcka/)). "Even if you can only afford $50, offer it" ([31pjcl](https://reddit.com/r/Filmmakers/comments/31pjcl/)).
2. **A script, not an idea.** "if I can see the movie while looking at the page" ([1p6p9pt](https://reddit.com/r/filmmaking/comments/1p6p9pt/)).
3. **A prepared, decisive director with a plan.** "nobody commits to a vibe, they commit to a plan" ([1wqp9pv](https://reddit.com/r/Filmmakers/comments/1wqp9pv/)).
4. **A step-up role or real title,** such as a gaffer getting to DP or an art assistant to PD ([1vabt10](https://reddit.com/r/Filmmakers/comments/1vabt10/)). This is a stronger signal than credit.
5. **A short commitment.** "Shorts, 1-3 days. It's trading favors" ([1koxl7u](https://reddit.com/r/filmmaking/comments/1koxl7u/)).
6. **Care: real food, transport, sane hours.** "If you can't pay people, feed people."
7. **Footage that is actually delivered,** with a date.
8. **Momentum.** "People love jumping on a moving train"; seeing who's already attached.
9. **The director's past finished work and online footprint.**
10. **Honesty about the budget.** "If you don't lie, nobody will accuse you of scamming" ([1mbu4zn](https://reddit.com/r/Filmmakers/comments/1mbu4zn/)).

People say no to: vague posts, "exposure" boilerplate, multi-day unpaid shoots, "this will be really fun", "meals when available, footage when possible", and one person listed as producer, writer, director and casting director all at once.

---

## 6. Role cheat-sheet

| Role | What they want | Red flags and dealbreakers | Must-see before saying yes | Supply |
|---|---|---|---|---|
| **Actors** (non-union, building a reel) | Usable reel footage delivered fast; a good role; paid or at least fed and reimbursed; sides early; the latest reasonable call time; a safe set | "Footage when possible"; deferred pay; nudity, head shave or weight loss with no intimacy coordinator; private-home callbacks; gmail "CAA/Netflix" castings; requests for personal data | Role breakdown; pay and expenses; dates and call window; local?; **footage due date**; content flags plus coordinator; sides; who directs (verified past work) | Heavily oversupplied (8,000 for 4); **female leads scarce in India** |
| **Screenwriters** | To be read, produced and credited; stay involved; keep feature rights; a fee if possible | Credit grabs; short-film contracts that grab feature rights; pay-to-play contests | Credit (sole, shared, story-by); rights scope; fee or option; production plan; director's finished work | Oversupplied ("all skills but no script lol") |
| **Directors seeking scripts** | Contained, producible scripts (1-2 locations, 10-15 min, or 80+ min features) | Expensive scripts; 60-page "features"; unclear chain of title | Logline, page count, locations and cast, rights, rewrite flexibility | Thin evidence |
| **Composers** | Honest scope; paid, or nobody else is paid; time; creative trust; a long-term director relationship | "DM me for info"; unpaid custom demos; 1-week feature deadline; everyone paid except music | Runtime; **minutes of music / number of cues**; picture-lock date; genre and style; temp track; pay parity | Oversupplied; remote |
| **Location sound** | A real rate plus a separate kit fee; a boom op budgeted; sound-aware choices; sane calls | $100-400 all-in with gear; one-person department next to a 4-person camera team; 5am call to record at noon | Day rate plus kit fee; boom op Y/N; speaking actors per scene; INT/EXT; hours and hard out | **Scarce**: "if only one person on set is getting paid, it's the sound mixer" |
| **Editors** | Clear scope and deliverables; a post end date; written communication; deposit; timestamped notes | "Are you available?" with no info; unpaid test edits; no end date; scope creep (shoot plus produce plus motion graphics) | Hours of footage; deliverables; **post schedule and end date**; revision rounds; rate and terms | Junior editors oversupplied in the US; **in-person editors scarce in India** |
| **VFX** | Brought in during pre-production; boards; per-shot pricing; realistic expectations | "Very easy shots"; "make it look like Hollywood"; credit as pay | **Number of shots** plus a description of each; boards; VFX supervisor on set; budget; deadline | Remote |
| **DPs** | Paid, or a step up plus strong reel material; a look agreed in pre-production; adequate crew (AC, G&E); respect; gear terms | "Gonna be really fun"; own gear for free; no AC; yelling; credit misassignment | Rate plus kit; who supplies the package; crew under the DP; prep days; shoot days, hours and locations; footage access | Oversupplied without gear; **DP with gear in demand** |
| **ACs, gaffers, G&E** | Clear role boundaries; fair rate; manageable hours; an experienced department | Doing gaffer and AC jobs on grip pay; volunteer novice departments | Department size and who they report to; rate and hours; gear list | **Gaffers scarce** |
| **ADs and PAs** | A clear title (1st vs 2nd AD); pay as agreed; a producer who signs | "Producer/PA" that is really an AD; booking cut on arrival; unpaid for 1.5 years | Exact title and duties; days; rate; who controls the money | **ADs scarce**; PAs oversupplied |
| **Producers** | A locked script; schedule and budget; known director or names; realistic scope | First-timers rushing a feature; "producers" charging to lend their name | Budget, schedule, script stage, attached people, financing | **Scarce** at indie level |
| **Directors seeking crew/cast** | Reliable, responsive people who read the breakdown; local or note travel; best clip first; kit owners | Flaking; unserious profiles (dating-style headshots); slow replies; copy-paste applications | Availability on the exact dates; rate and kit; distance; role reel; reliability | n/a (the hiring side) |

---

## 7. India-specific notes

- **Communities:**
  - r/indianFilmmakers (about 11k members, very active in 2026, with daily team and casting posts and an official subreddit film festival).
  - City subs (mumbai, pune, delhi, bangalore, hyderabad, Chennai, kolkata) host calls, but also attract jokes and creeps.
  - Regional subs (MalayalamMovies, KannadaMovies, tollywood, kollywood); r/IsThisAScamIndia; new micro-subs (r/BangaloreFilmclub, r/PuneFilmFusion).
- **WhatsApp is the operating system.** Groups, communities and links get posted in threads. The problems: newcomers can't see history, groups go inactive, duplicates multiply, and phone numbers end up public. Telegram was paused for a stretch in 2026 ([1u51cvf](https://reddit.com/r/indianFilmmakers/comments/1u51cvf/)). **Work with WhatsApp (share-out), don't replace it.**
- **Hyperlocal.** "Strictly Pune", "near Saket metro", Dwarka, Jayanagar parks ([1tn4fw9](https://reddit.com/r/indianFilmmakers/comments/1tn4fw9/), [1vuauon](https://reddit.com/r/indianFilmmakers/comments/1vuauon/)). Some roles must sit together (director and editor: "Nai yaar editor saath me chaiye mujhe", Hindi for "No, I need the editor with me", [1vhr8kq](https://reddit.com/r/indianFilmmakers/comments/1vhr8kq/)). Writers and composers happily work remotely.
- **Language splits crews.** Hindi, Telugu, Tamil, Malayalam, Kannada, Bengali and Marathi crews rarely mix. A non-Tamil AD lost a role over translation ([1vpwkqd](https://reddit.com/r/indianFilmmakers/comments/1vpwkqd/)). Calls state their language; posts are written in Hinglish and Tenglish.
- **Who the users are:**
  - Mostly 18-26-year-old students and IT or engineering workers, many free only on weekends.
  - Many hide filmmaking from family ("double life", [1ixmq2m](https://reddit.com/r/hyderabad/comments/1ixmq2m/)).
  - Women face extra family pressure.
  - Even an AD with an Amazon Prime credit struggles to find the next gig ([1uyhaom](https://reddit.com/r/indianFilmmakers/comments/1uyhaom/)).
- **Money norms:**
  - "Collaborative/unpaid" is the default for shorts. The respectful baseline is travel allowance, food and credit ([1wj0n3r](https://reddit.com/r/indianFilmmakers/comments/1wj0n3r/)).
  - Budgets run Rs 1k-20k.
  - Real costs are locations, food and travel. One person took a Rs 7 lakh loan ([1v5x3l7](https://reddit.com/r/indianFilmmakers/comments/1v5x3l7/)).
  - Friend crews fight over costs ([1td199h](https://reddit.com/r/Filmmakers/comments/1td199h/)).
  - Net 90 is "good days" ([1u6jsx7](https://reddit.com/r/Filmmakers/comments/1u6jsx7/)).
  - Freelancer contracts aren't normal, so frame agreements as a **booking confirmation**, not a contract.
  - Payments are UPI-native.
- **Scam signatures:**
  - Instagram ads impersonating channels or labels.
  - "Shortlisted" messages followed by Rs 500-8,000 registration, security or professional fees.
  - Paid workshops required "before auditions"; portfolio-shoot upsells.
  - Google Meet "orientations" that are really sales pitches.
  - Requests for "bold" photos; night-time street approaches by fake "short film directors".
  - The community rule: never pay to audition ([1tumcb1](https://reddit.com/r/acting/comments/1tumcb1/), [1vgzfim](https://reddit.com/r/mumbai/comments/1vgzfim/), [1jgaubm](https://reddit.com/r/hyderabad/comments/1jgaubm/)).
- **Safety unlocks supply.** Female leads are the most requested and scarcest role, in Pune, Hyderabad, Ahmedabad and Mumbai ([1tnd9ev](https://reddit.com/r/pune/comments/1tnd9ev/)). Organisers already improvise norms: "safe public place", "bring friends for comfort", "text, don't call" ([1w8nk3i](https://reddit.com/r/indianFilmmakers/comments/1w8nk3i/), [1t3prwe](https://reddit.com/r/indianFilmmakers/comments/1t3prwe/)). Building those norms into the product is both a trust feature and a growth lever.
- **Role imbalance.** Too many writers; too few editors, sound people, gear owners and female actors ([1uhya0h](https://reddit.com/r/indianFilmmakers/comments/1uhya0h/), [1vfwy1v](https://reddit.com/r/indianFilmmakers/comments/1vfwy1v/)). Recruit scarce roles first.
- **Moments and hubs:** the IFP 50-Hour challenge (September, with a vertical/mobile category; team-formation spikes in Pune, Mumbai, Delhi/NCR and Indore), college 48-hour sprints (LUMIERE), MAMI, FHF at Regal, Versova screenings, PIFF, BISFF, theatre groups and acting workshops.
- **Trust signals already in use:** SWA script registration ("SWA registered… happy to share privately", [1v2znnw](https://reddit.com/r/indianFilmmakers/comments/1v2znnw/)); paid courses (Ramanaidu, Tent Cinema) treated as legitimacy.
- **Small cities** (Amravati, Mysuru, Indore, Dehradun, Amritsar, Kochi, Nagpur, Nashik, Chandigarh) have active but sparse demand. The app's chance is to beat "move to Mumbai".
- **Pricing sensitivity.** An hour of coaching at Rs 5,000 is about 13 days' minimum wage ([1dxindt](https://reddit.com/r/acting/comments/1dxindt/)).
- **UK/US contrast:** in the West, film school and unions provide structure, and the pain is "my degree isn't enough". In India, few beginners have access to film school, so the pain is "I can't find four people to shoot with", and safety and language problems are worse.

---

## 8. Design principles (what the research says to build)

1. **Projects first, faces never first.** Swipe cards are a *role on a project*. Crew cards lead with reels, kit and credits. Actor headshots appear only on gigs the actor applied to.
2. **Terms before the swipe.** Pay type and amount, dates, area, hours and what you get go on the card face. No terms, no post. An honest "unpaid" beats vague perks.
3. **Booked, not matched.** A mutual yes is "in talks". The real event is a two-tap booking card accepted by whoever controls the money. No "It's a match!".
4. **Follow through to wrap.** Footage, credit and pay are tracked like invoices with due dates. Wrap is the retention hook ("keep and rebook").
5. **Free for talent, forever.** No fees to apply, be seen, message or get verified. No paid boosts. Publish how the app makes money.
6. **Density over reach.** One city at a time, with honest counts and neighbourhood-level radius. Remote post roles fill thin markets.
7. **Asymmetric by role.** For scarce roles, hirers pitch and scout; for oversupplied roles, hirers filter. Fill key roles first (producer or AD, DP, sound).
8. **Fewer, better applications.** Hard gates, caps, and a daily like limit. Every applicant gets an answer.
9. **Reputation as verified facts.** Double-blind, aggregated, shown only above a minimum sample. Both sides are accountable. It follows the person, not the LLC.
10. **Safety through rules, not trust in strangers.** Block fees to talent. Warn on moves off the platform and on body or photo requests. Enforce audition venue rules. Badges say exactly what they mean.
11. **Work with WhatsApp.** Share-out text, tappable addresses, calendar invites. Day sheets work without the app installed.
12. **A frictionless first minute.** Browse without an account. Ask only for role, city and one link. Android-first, with no forced social login.
13. **Human-made.** No gen-AI on users' work, no AI-written messages, and a visible practitioner founder.
14. **Do one thing well.** Crew up and make the shoot happen. No feed, gear marketplace or production suite.
15. **India-native.** Language-aware, weekend-aware, UPI-friendly, and framed as a "booking confirmation", not a contract.

### Never do (anti-patterns repeated across angles)
- "Tinder" branding, photo-first cards or flirty copy.
- Pay-to-apply, boosts, or paid badges or verification.
- A global unstructured feed.
- Allowing "collab", "DM for details", "exposure", or credit/IMDb/copy as the only perk.
- Public star ratings or free-text name-and-shame.
- Mandatory prescreens, demos or test edits for unpaid roles.
- Exposing phone numbers or emails.
- Letting deals live only in chat.
- A Swiss-army-knife scope with no users.
- Fake activity or seeded bots in production.
- An iOS-first or web-only build.
- A forced full profile at signup.
- Launching through self-promotional Reddit posts.
- US-only legal and payment assumptions.
- Accountability for one side only.

---

## 9. Open questions (to validate in the first 90 days)

1. **Should the owner side swipe or scan a list?** Hirers drown in volume, and a swipe deck is fast, but casting directors also want side-by-side comparison. Test both in the Crew-up view.
2. **Will owners accept mandatory pay and logistics fields,** or abandon the post? Measure drop-off at the publish step. Research (r/filmscoring flair, r/crewwanted format proposals) suggests they will be welcomed.
3. **Does a token stipend nudge measurably reduce no-shows?** Research says yes anecdotally ("even $50"). Instrument show-up rate by comp type.
4. **What happens when an owner never delivers footage?** Is a visible "footage delivered 2/6" enough deterrence, or are deposits or escrow needed?
5. **ID verification in India:** which vendor, and what's legally allowed? Aadhaar-based checks are restricted. Start with a concierge video check and validate cost and benefit before buying a vendor.
6. **Mumbai first or Bengaluru first?** Mumbai has the industry and Versova/Andheri density. Bengaluru has IT-worker weekend filmmakers and an active Telegram-to-Reddit community.
7. **How do we recruit scarce roles** (sound, gaffers, editors, female actors) before demand shows up? Theatre groups, acting studios, rental houses, mass-media colleges?
8. **Will remote post roles (editor, composer) dilute local density,** or rescue small towns?
9. **Does "Keep / rebook" produce returning crews,** which is the retention mechanic, or do teams move to WhatsApp after the first shoot? Either outcome is acceptable if they start the next project in the app.
10. **What are the dispute and appeal flows** when a "not received" or "no-show" is contested? What is the moderation load per 1,000 bookings?
11. **Brand and legal:** trademark check for the chosen name; whether the free-to-apply model keeps us outside advance-fee talent-service laws (Krekorian Act, CA) and Indian equivalents (needs a lawyer); work-for-hire language that is valid in India.
12. **Monetization tolerance:** will owners with budgets pay for production tools or payment protection, given that low-budget users "won't pay for more scheduling software"?

---

## 10. Source files
- [finding-crew.md](raw/finding-crew.md): 61 threads; channels, yes-factors, time-to-staff
- [flakes-trust-pay.md](raw/flakes-trust-pay.md): 44 threads; deliverables, pay, reliability record design
- [existing-platforms.md](raw/existing-platforms.md): 35 threads plus web; competitor pricing, ratings, graveyard
- [roles-perspectives.md](raw/roles-perspectives.md): 29 threads; role cheat-sheet, role-specific fields
- [beginners-students-india.md](raw/beginners-students-india.md): 41 threads; India, beginners, small towns
- [post-match-workflow.md](raw/post-match-workflow.md): 48 threads; booking card, day sheet, wrap checklist
- [app-idea-reception.md](raw/app-idea-reception.md): 27 threads; launch reception, design principles
- [profile-matching-signals.md](raw/profile-matching-signals.md): 29 threads; schemas, filters, scoring heuristic
- [safety-growth-money.md](raw/safety-growth-money.md): 26 threads; safety ranking, cold start, monetization
