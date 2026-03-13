// filter.js
(function() {
    // Inject CSS
    const style = document.createElement('style');
    style.innerHTML = `
        #studentFilterModalOverlay {
            position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
            background: rgba(15, 23, 42, 0.6); backdrop-filter: blur(2px);
            display: none; justify-content: center; align-items: center; z-index: 99999;
            font-family: 'Segoe UI', Arial, Helvetica, sans-serif;
        }
        #studentFilterModal {
            background: #ffffff; padding: 28px; border-radius: 12px;
            width: 360px; border: 1px solid #e2e8f0; 
            box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1); color: #1e293b;
        }
        #studentFilterModal h3 { margin-top: 0; text-align: center; color: #1e293b; font-weight: 800; margin-bottom: 20px; }
        .sf-label { display: block; margin-bottom: 6px; font-weight: 600; font-size: 14px; }
        .sf-select {
            width: 100%; padding: 10px; margin-bottom: 15px; border-radius: 8px;
            border: 1px solid #cbd5e1; background: #f8fafc; color: #1e293b; 
            font-size: 14px; outline: none; box-sizing: border-box;
            transition: border-color 0.2s ease;
        }
        .sf-select:focus { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1); }
        .sf-btn {
            width: 100%; padding: 10px; border-radius: 8px; font-weight: 600;
            cursor: pointer; transition: all 0.2s; font-size: 14px; border: none;
            margin-bottom: 10px; box-sizing: border-box;
        }
        .sf-btn-primary { background: #2563eb; color: #ffffff; }
        .sf-btn-primary:hover:not(:disabled) { background: #1d4ed8; transform: translateY(-1px); box-shadow: 0 4px 6px rgba(37, 99, 235, 0.4); }
        .sf-btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
        .sf-btn-ghost { background: #f1f5f9; color: #64748b; border: 1px solid #cbd5e1; }
        .sf-btn-ghost:hover { background: #e2e8f0; color: #1e293b; }
    `;
    document.head.appendChild(style);

    // Inject HTML
    const modalHTML = `
        <div id="studentFilterModalOverlay">
            <div id="studentFilterModal">
                <h3>Select Student</h3>
                
                <label class="sf-label">Department</label>
                <select id="sf_dept" class="sf-select"><option value="">-- All --</option></select>
                
                <label class="sf-label">Passing Year</label>
                <select id="sf_year" class="sf-select"><option value="">-- All --</option></select>
                
                <label class="sf-label">Semester</label>
                <select id="sf_sem" class="sf-select"><option value="">-- All --</option></select>
                
                <button id="sf_searchBtn" class="sf-btn sf-btn-ghost" style="margin-bottom: 20px;">Filter Students</button>
                
                <label class="sf-label">Student Name</label>
                <select id="sf_name" class="sf-select" disabled><option value="">Select a student...</option></select>
                
                <button id="sf_proceedBtn" class="sf-btn sf-btn-primary" disabled>Proceed with Selected</button>
                <button id="sf_addAllBtn" class="sf-btn sf-btn-primary" style="background:#10b981; margin-bottom: 20px;" disabled>Add All Filtered Students</button>
                <button id="sf_cancelBtn" class="sf-btn sf-btn-ghost" style="margin-bottom: 0px;">Manual Entry / Cancel</button>
            </div>
        </div>
    `;
    document.body.insertAdjacentHTML('beforeend', modalHTML);

    const overlay = document.getElementById('studentFilterModalOverlay');
    const deptSelect = document.getElementById('sf_dept');
    const yearSelect = document.getElementById('sf_year');
    const semSelect = document.getElementById('sf_sem');
    const nameSelect = document.getElementById('sf_name');
    const searchBtn = document.getElementById('sf_searchBtn');
    const proceedBtn = document.getElementById('sf_proceedBtn');
    const addAllBtn = document.getElementById('sf_addAllBtn');
    const cancelBtn = document.getElementById('sf_cancelBtn');

    let currentResolve = null;
    let studentsCache = [];

    async function loadOptions() {
        try {
            const res = await fetch('/student/filter-options');
            const data = await res.json();
            
            const pop = (sel, arr) => {
                sel.innerHTML = '<option value="">-- All --</option>';
                arr.forEach(val => {
                    if (val) sel.innerHTML += `<option value="${val}">${val}</option>`;
                });
            };
            
            pop(deptSelect, data.departments || []);
            pop(yearSelect, data.passingYears || []);
            pop(semSelect, data.semesters || []);
        } catch (e) {
            console.error("Failed to load filter options", e);
        }
    }

    searchBtn.onclick = async () => {
        searchBtn.textContent = "Filtering...";
        searchBtn.disabled = true;
        try {
            const d = encodeURIComponent(deptSelect.value);
            const y = encodeURIComponent(yearSelect.value);
            const s = encodeURIComponent(semSelect.value);
            
            const res = await fetch(`/student/filter?department=${d}&passingYear=${y}&semester=${s}`);
            studentsCache = await res.json();
            
            nameSelect.innerHTML = '<option value="">Select a student...</option>';
            studentsCache.forEach((st, idx) => {
                const rollStr = st.roll ? ` (${st.roll})` : '';
                nameSelect.innerHTML += `<option value="${idx}">${st.name}${rollStr}</option>`;
            });
            
            nameSelect.disabled = studentsCache.length === 0;
            proceedBtn.disabled = true;
            addAllBtn.disabled = studentsCache.length === 0;
            if (studentsCache.length === 0) alert("No students found matching these criteria.");
        } catch (e) {
            console.error(e);
            alert("Error filtering students");
        } finally {
            searchBtn.textContent = "Filter Students";
            searchBtn.disabled = false;
        }
    };

    nameSelect.onchange = () => {
        proceedBtn.disabled = !nameSelect.value;
    };

    proceedBtn.onclick = () => {
        const student = studentsCache[nameSelect.value];
        if (student && currentResolve) {
            currentResolve([student]); // return as array
            closeModal();
        }
    };

    addAllBtn.onclick = () => {
        if (studentsCache.length > 0 && currentResolve) {
            currentResolve([...studentsCache]); // return all sorted/filtered cache
            closeModal();
        }
    };

    cancelBtn.onclick = () => {
        if (currentResolve) currentResolve(null);
        closeModal();
    };

    function closeModal() {
        overlay.style.display = 'none';
        currentResolve = null;
        nameSelect.innerHTML = '<option value="">Select a student...</option>';
        nameSelect.disabled = true;
        proceedBtn.disabled = true;
        addAllBtn.disabled = true;
        deptSelect.value = '';
        yearSelect.value = '';
        semSelect.value = '';
    }

    window.showFilterModal = function() {
        return new Promise((resolve) => {
            currentResolve = resolve;
            overlay.style.display = 'flex';
            loadOptions();
        });
    };
})();
