/* ---- Ready-made ProHost replies ---- */
const PROHOST_TEMPLATES = [
  { title: 'Send the app link when someone comments', hint: 'Public reply + private DM with the Google Play link', r: { name: 'Play link on comment', trigger: 'comment', keywords: ['link', 'app', 'download'], commentReplies: ['Sent you a DM! 📩', 'Check your DMs 👋'], privateReply: "Here's the ProHost app: {link_play}\nProHost for iPhone is coming soon.", tags: ['lead'], priority: 1 } },
  { title: 'Story reply thank-you', hint: 'Answers anyone who replies to a story', r: { name: 'Story reply thanks', trigger: 'story_reply', keywords: [], dm: 'Thanks for the reply, {first_name}! Get ProHost on Google Play: {link_play} (iPhone coming soon).', tags: ['story'] } },
  { title: 'Welcome DM with quick choices', hint: 'First message from a new person', r: { name: 'First DM welcome', trigger: 'dm_welcome', keywords: [], dm: 'Hi {first_name}! Welcome to ProHost, where you rent or list spaces by the hour, day or month. Which are you?', quickReplies: ['Host', 'Professional'], tags: ['welcome'] } },
  { title: 'I want to list my space', hint: 'Host questions, with the launch offer', r: { name: 'Hosts: how to list', trigger: 'dm_keyword', keywords: ['host', 'list my space', 'landlord', 'owner'], dm: 'Great! You publish your space with ProHost Premium. Right now: 4 months free on the monthly plan, or 50% off your first year. Start here: {link_play}', buttonLabel: 'Get ProHost', buttonUrl: 'https://pro-host.tech/ig', tags: ['host'], priority: 2 } },
  { title: 'I need a room', hint: 'Professionals looking for a space', r: { name: 'Professionals: find a room', trigger: 'dm_keyword', keywords: ['professional', 'need a room', 'rent a room', 'book'], dm: 'Browsing and booking is free on ProHost. Search rooms by the hour, day or month, then send a booking request: {link_play}', tags: ['professional'], priority: 2 } },
  { title: 'Is there an iPhone version?', hint: 'iOS question', r: { name: 'iOS coming soon', trigger: 'dm_keyword', keywords: ['ios', 'iphone', 'apple', 'app store'], dm: 'ProHost for iPhone is coming soon! Meanwhile it is on Google Play: {link_play}', tags: ['ios'], priority: 3 } },
  { title: 'Anyone who mentions us in a story', hint: 'Thank-you DM', r: { name: 'Story mention thanks', trigger: 'story_mention', keywords: [], dm: 'Thank you for the mention, {first_name}! 🙌 Get ProHost: {link_play}', tags: ['mention'] } },
  { title: 'Catch-all DM answer', hint: 'Anything else, once a day', r: { name: 'Default DM answer', trigger: 'dm_default', keywords: [], dm: 'Thanks for writing! Get the app here: {link_play}. A person from our team will follow up if you need more help.', priority: -1, cooldownHours: 24 } },
];


/* Generic starters for any other brand (no links of their own yet: use {first_name} and add a button). */
const GENERIC_TEMPLATES = [
  { title: 'Welcome DM', hint: 'First message from a new person', r: { name: 'First DM welcome', trigger: 'dm_welcome', keywords: [], dm: 'Hi {first_name}! Thanks for writing. How can we help?', tags: ['welcome'] } },
  { title: 'Story reply thank-you', hint: 'Answers anyone who replies to a story', r: { name: 'Story reply thanks', trigger: 'story_reply', keywords: [], dm: 'Thanks for the reply, {first_name}! 🙌', tags: ['story'] } },
  { title: 'Comment: send details by DM', hint: 'Public reply + private DM when someone asks for info', r: { name: 'Info on comment', trigger: 'comment', keywords: ['info', 'price', 'details', 'link'], commentReplies: ['Sent you a DM! 📩', 'Check your DMs 👋'], privateReply: 'Hi {first_name}, here are the details you asked for.', tags: ['lead'], priority: 1 } },
  { title: 'Catch-all DM answer', hint: 'Anything else, once a day', r: { name: 'Default DM answer', trigger: 'dm_default', keywords: [], dm: 'Thanks for writing! A member of our team will follow up if you need more help.', priority: -1, cooldownHours: 24 } },
];
const templates = () => (S.brand === LEGACY ? PROHOST_TEMPLATES : GENERIC_TEMPLATES);
