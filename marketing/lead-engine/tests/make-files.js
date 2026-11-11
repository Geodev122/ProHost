// Writes the test exports into ./out (run: node make-files.js)
const fs = require('fs'); const path = require('path'); const X = require('xlsx');
const OUT = path.join(__dirname, 'out'); fs.mkdirSync(OUT, { recursive: true }); process.chdir(OUT);
const csv = rows => rows.map(r => r.map(v => /[",\n]/.test(String(v)) ? '"' + String(v).replace(/"/g, '""') + '"' : v).join(',')).join('\r\n') + '\r\n';
const H = ['First Name','Last Name','Title','Company','Company Name for Emails','Email','Email Status','Seniority','# Employees','Industry','Keywords','Person Linkedin Url','Website','City','Country','Mobile Phone','SEO Description','Latest Funding','Apollo Contact Id'];
// 1st Apollo export
fs.writeFileSync('apollo1.csv', csv([H,
 ['Rami','Aoun','Managing Director','Skyline Properties S.A.L.','Skyline Properties','rami.aoun@skylineprops.example','Verified','founder','45','real estate','property management, office space, business center','http://lb.linkedin.com/in/rami-aoun/','https://www.skylineprops.example/en/home','Beirut','Lebanon','+961 3 123 456','Manages offices in Achrafieh','','a1'],
 ['MAYA','HADDAD','Operations Director','Gulf Suites Hotel Apartments LLC','Gulf Suites','maya@gulfsuites.example; maya.h@gmail.com','Verified','director','230','hospitality','hotel apartments, meetings and events','linkedin.com/in/maya-haddad-123','gulfsuites.example','Dubai','United Arab Emirates','+971 50 111 2222','Serviced apartments in JLT','Series A','a2'],
 ['Dr. Karim','Nasr','Medical Director','Cedars Polyclinic','','karim.nasr@cedarspoly.example','Unverified','director','60','hospital & health care','polyclinic, outpatient clinics','https://www.linkedin.com/in/karimnasr','cedarspoly.example','Beirut','Lebanon','','Polyclinic in Hamra','','a3'],
 ['Front','Office','Receptionist','Najd Towers','','info@najdtowers.example','Verified','entry','80','real estate','commercial property','','najdtowers.example','Riyadh','Saudi Arabia','','Office towers','','a4'],
 ['Lina','Saleh','CEO','Najd Towers','','email_not_unlocked@domain.com','Unavailable','c_suite','80','real estate','commercial property','https://www.linkedin.com/in/lina-saleh','najdtowers.example','Riyadh','Saudi Arabia','','Office towers','','a5'],
 ['Omar','Fares','General Manager','Najd Towers','','email_not_unlocked@domain.com','Unavailable','c_suite','80','real estate','commercial property','https://www.linkedin.com/in/omar-fares','najdtowers.example','Riyadh','Saudi Arabia','','Office towers','','a6'],
 ['Rami','Aoun','Managing Director','Skyline Properties S.A.L.','Skyline Properties','RAMI.AOUN@skylineprops.example','Verified','founder','45','real estate','property management, office space, business center','','skylineprops.example','Beirut','Lebanon','','','','a1'],
 ['','','','','','','','','','','','','','','','','','','a7'],
]));
// 2nd Apollo export: title change, status change, unchanged, 2 new, LinkedIn-only match, name+company match
fs.writeFileSync('apollo2.csv', csv([H,
 ['Rami','Aoun','Chief Executive Officer','Skyline Properties S.A.L.','Skyline Properties','rami.aoun@skylineprops.example','Verified','c_suite','45','real estate','property management, office space, business center','http://lb.linkedin.com/in/rami-aoun/','https://www.skylineprops.example/en/home','Beirut','Lebanon','+961 3 123 456','Manages offices in Achrafieh','','a1'],
 ['Maya','Haddad','Operations Director','Gulf Suites Hotel Apartments LLC','Gulf Suites','maya@gulfsuites.example','Verified','director','230','hospitality','hotel apartments, meetings and events','linkedin.com/in/maya-haddad-123','gulfsuites.example','Dubai','United Arab Emirates','+971 50 111 2222','Serviced apartments in JLT','Series A','a2'],
 ['Karim','Nasr','Medical Director','Cedars Polyclinic','','karim.nasr@cedarspoly.example','Catch-all','director','60','hospital & health care','polyclinic, outpatient clinics','https://www.linkedin.com/in/karimnasr','cedarspoly.example','Beirut','Lebanon','','Polyclinic in Hamra','','a3'],
 ['Lina','Saleh','CEO','Najd Towers','','lina.saleh@najdtowers.example','Verified','c_suite','80','real estate','commercial property','https://www.linkedin.com/in/lina-saleh/','najdtowers.example','Riyadh','Saudi Arabia','','Office towers','','a5'],
 ['Omar','Fares','General Manager','Najd Towers','','','','c_suite','90','real estate','commercial property','','najdtowers.example','Riyadh','Saudi Arabia','','Office towers','','a6'],
 ['Hana','Khalil','Leasing Manager','Marina Business Center','','hana@marinabc.example','Verified','manager','25','real estate','business center, coworking','https://www.linkedin.com/in/hana-khalil','marinabc.example','Dubai','UAE','','Business center in Dubai Marina','','a8'],
 ['Tarek','Mansour','Founder','Hamra Cowork','','tarek@hamracowork.example','Verified','founder','12','real estate','coworking, flexible workspace','https://www.linkedin.com/in/tarek-mansour','hamracowork.example','Beirut','Lebanon','','Coworking in Hamra','','a9'],
]));
// Excel: notes sheet + leads sheet with header on row 3
const wb = X.utils.book_new();
X.utils.book_append_sheet(wb, X.utils.aoa_to_sheet([['Exported for ProHost'], ['Do not edit']]), 'Notes');
X.utils.book_append_sheet(wb, X.utils.aoa_to_sheet([['Q4 hospitality list'], [''], ['Full Name','Job Title','Organization Name','Work Email','Company Size','Industry','LinkedIn','Location','Headcount note'],
  ['Sara Itani','Hotel Manager','Phoenicia Grand Hotel','sara.itani@phoenicia-grand.example','201-500','hospitality','https://www.linkedin.com/in/sara-itani','Beirut, Lebanon','x'],
  ['Youssef Ali','Director of Sales','Red Sea Resort','youssef@redsearesort.example','1,001-5,000','hospitality','www.linkedin.com/in/youssef-ali','Jeddah, Saudi Arabia','y']]), 'Leads');
X.writeFile(wb, 'hotels.xlsx');
// PDL JSON lines with nested fields
fs.writeFileSync('pdl.jsonl', [
  { full_name: 'nour el hage', job_title: 'property manager', job_title_levels: ['manager'], job_company_name: 'Achrafieh Estates', job_company_website: 'achrafieh-estates.example', job_company_size: '11-50', job_company_industry: 'real estate', work_email: 'nour@achrafieh-estates.example', linkedin_url: 'linkedin.com/in/nour-el-hage', location_country: 'lebanon', location_locality: 'beirut', emails: [{ address: 'nour.personal@gmail.com', type: 'personal' }] },
  { full_name: 'faisal al harbi', job_title: 'asset manager', job_title_levels: ['manager'], job_company_name: 'Riyadh Office Parks', job_company_website: 'riyadhparks.example', job_company_size: '51-200', job_company_industry: 'commercial real estate', work_email: 'faisal@riyadhparks.example', linkedin_url: 'linkedin.com/in/faisal-al-harbi', location_country: 'saudi arabia', location_locality: 'riyadh' },
].map(o => JSON.stringify(o)).join('\n') + '\n');
// UTF-16LE tab-separated (Excel "Unicode text")
const u16 = 'Name\tTitle\tCompany\tEmail\tCountry\r\nجورج خوري\tOwner\tبيت الضيافة\tgeorges@beitdiyafa.example\tLebanon\r\n';
fs.writeFileSync('unicode.txt', Buffer.concat([Buffer.from([0xFF, 0xFE]), Buffer.from(u16, 'utf16le')]));
// Windows-1252 semicolon CSV
fs.writeFileSync('latin1.csv', Buffer.from('Prénom;Nom;Poste;Société;E-mail;Pays\r\nJosé;Gemayel;Directeur général;Hôtel Montréal;jose@hotelmontreal.example;Liban\r\n', 'latin1'));
// Verifier result file
fs.writeFileSync('neverbounce.csv', 'email,result\nkarim.nasr@cedarspoly.example,valid\nhana@marinabc.example,invalid\n');
console.log(fs.readdirSync('.').join(' '));
